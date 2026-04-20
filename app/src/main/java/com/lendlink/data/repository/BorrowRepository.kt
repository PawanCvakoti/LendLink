package com.lendlink.data.repository

import com.google.firebase.database.*
import com.lendlink.data.local.BorrowDao
import com.lendlink.data.local.LendHistoryDao
import com.lendlink.data.local.BorrowHistoryDao
import com.lendlink.data.local.LenderCreditHistoryDao
import com.lendlink.data.local.BorrowerPaymentHistoryDao
import com.lendlink.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class BorrowRepository(
    private val borrowDao: BorrowDao,
    private val creditDao: LenderCreditHistoryDao,
    private val paymentDao: BorrowerPaymentHistoryDao,
    private val lendHistoryDao: LendHistoryDao,
    private val borrowHistoryDao: BorrowHistoryDao
) {
    private val db = FirebaseDatabase.getInstance().reference

    // ── BORROW ITEM ───────────────────────────────────────────
    suspend fun borrowItem(item: Item, borrower: User): Result<Unit> = runCatching {
        val borrowerUid = borrower.uid
        val lenderUid = item.lenderId
        val itemId = item.itemId

        // 1. Check wallet balance (pre-check)
        val walletSnap = db.child("wallets/$borrowerUid/balance").get().await()
        val balance = walletSnap.getValue(Long::class.java) ?: 0L
        if (balance < item.price) throw Exception("Insufficient credits (Need ₩${item.price})")

        val recordId = db.child("borrow_records").push().key ?: throw Exception("Record error")
        val now = System.currentTimeMillis()
        val deadline = now + (7L * 24 * 60 * 60 * 1000)

        // 2. Rigid Transaction on the item node to prevent double-borrowing
        val itemRef = db.child("items/$itemId")
        val transactionDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()

        itemRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentStatus = mutableData.child("status").getValue(String::class.java)
                if (currentStatus != "available") {
                    return Transaction.abort() // Someone else borrowed it first
                }

                // Update item status and borrower info atomically
                mutableData.child("status").value = "lent"
                mutableData.child("borrowerId").value = borrowerUid
                mutableData.child("borrowerName").value = borrower.username
                mutableData.child("borrowerPhone").value = borrower.phone
                mutableData.child("borrowerLocation").value = borrower.locationAddress
                mutableData.child("borrowedAt").value = now
                mutableData.child("deadline").value = deadline

                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) transactionDeferred.completeExceptionally(error.toException())
                else transactionDeferred.complete(committed)
            }
        })

        if (!transactionDeferred.await()) {
            throw Exception("Item is no longer available.")
        }

        // 3. Perform atomic multi-path update for wallets and records
        val record = BorrowRecord(
            recordId = recordId, itemId = itemId, itemName = item.name, itemImageUrl = item.imageUrl,
            itemCategory = item.category, // Added category
            lenderId = lenderUid, lenderName = item.lenderName, lenderPhone = item.lenderPhone, lenderLocation = item.lenderLocation,
            borrowerId = borrowerUid, borrowerName = borrower.username, borrowerPhone = borrower.phone, borrowerLocation = borrower.locationAddress,
            price = item.price, borrowedAt = now, deadline = deadline, status = "active"
        )

        val updates = hashMapOf<String, Any?>(
            "borrow_records/$recordId" to record,
            "wallets/$borrowerUid/balance" to ServerValue.increment(-item.price),
            "wallets/$lenderUid/balance" to ServerValue.increment(item.price)
        )
        db.updateChildren(updates).await()

        // 4. Log histories (Log these to separate nodes)
        val creditLog = LenderCreditHistory(
            entryId = db.child("lender_credit_history/$lenderUid").push().key ?: "",
            lenderId = lenderUid, amount = item.price, type = "borrow_payment",
            description = "₩${item.price} received from ${borrower.username} for ${item.name}",
            timestamp = now
        )
        db.child("lender_credit_history/$lenderUid/${creditLog.entryId}").setValue(creditLog)

        val paymentLog = BorrowerPaymentHistory(
            entryId = db.child("borrower_payment_history/$borrowerUid").push().key ?: "",
            borrowerId = borrowerUid, amount = item.price, type = "payment",
            description = "₩${item.price} paid to ${item.lenderName} for borrowing ${item.name}",
            timestamp = now
        )
        db.child("borrower_payment_history/$borrowerUid/${paymentLog.entryId}").setValue(paymentLog)

        // 5. Send notification to lender
        sendNotification(lenderUid, "Item Borrowed", "${borrower.username} borrowed your ${item.name}", "borrowed")
    }

    // ── RETURN PROCESS ────────────────────────────────────────
    suspend fun requestReturn(record: BorrowRecord): Result<Unit> = runCatching {
        val reqId = db.child("return_requests").push().key ?: ""
        val request = ReturnRequest(
            requestId = reqId,
            recordId = record.recordId,
            itemId = record.itemId,
            lenderId = record.lenderId,
            borrowerId = record.borrowerId,
            requestedAt = System.currentTimeMillis()
        )
        
        db.child("return_requests/$reqId").setValue(request).await()
        db.child("borrow_records/${record.recordId}/status").setValue("return_requested").await()
        
        sendNotification(record.lenderId, "Return Request", "${record.borrowerName} wants to return ${record.itemName}", "return_request")
    }

    suspend fun acceptReturn(record: BorrowRecord, requestId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val updates = hashMapOf<String, Any?>(
            "items/${record.itemId}/status" to "available",
            "items/${record.itemId}/borrowerId" to "",
            "items/${record.itemId}/borrowerName" to "",
            "items/${record.itemId}/borrowerPhone" to "",
            "items/${record.itemId}/borrowerLocation" to "",
            "items/${record.itemId}/borrowedAt" to 0L,
            "items/${record.itemId}/deadline" to 0L,
            
            "borrow_records/${record.recordId}" to null,
            "return_requests/$requestId" to null
        )
        db.updateChildren(updates).await()

        // Archive to history
        val historyId = record.recordId
        val lendH = LendHistory(
            historyId = historyId,
            lenderId = record.lenderId,
            itemId = record.itemId,
            itemName = record.itemName,
            itemImageUrl = record.itemImageUrl,
            itemCategory = record.itemCategory,
            borrowerName = record.borrowerName,
            borrowerId = record.borrowerId,
            borrowerPhone = record.borrowerPhone,
            borrowerLocation = record.borrowerLocation,
            price = record.price,
            lentAt = record.borrowedAt,
            returnedAt = now,
            recordId = record.recordId
        )
        val borrowH = BorrowHistory(
            historyId = historyId,
            borrowerId = record.borrowerId,
            itemId = record.itemId,
            itemName = record.itemName,
            itemImageUrl = record.itemImageUrl,
            itemCategory = record.itemCategory,
            lenderName = record.lenderName,
            lenderId = record.lenderId,
            lenderPhone = record.lenderPhone,
            lenderLocation = record.lenderLocation,
            price = record.price,
            borrowedAt = record.borrowedAt,
            returnedAt = now,
            recordId = record.recordId
        )
        db.child("lend_history/${record.lenderId}/$historyId").setValue(lendH)
        db.child("borrow_history/${record.borrowerId}/$historyId").setValue(borrowH)
        
        sendNotification(record.borrowerId, "Return Confirmed", "Lender accepted return of ${record.itemName}", "return_confirmed")
    }

    suspend fun applyPenalty(record: BorrowRecord, amount: Long): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val borrowerUid = record.borrowerId
        val lenderUid = record.lenderId

        val walletSnap = db.child("wallets/$borrowerUid/balance").get().await()
        val balance = walletSnap.getValue(Long::class.java) ?: 0L
        
        val updates = hashMapOf<String, Any?>(
            "wallets/$borrowerUid/balance" to (balance - amount),
            "wallets/$lenderUid/balance" to ServerValue.increment(amount)
        )
        db.updateChildren(updates).await()

        val creditLog = LenderCreditHistory(
            entryId = db.child("lender_credit_history/$lenderUid").push().key ?: "",
            lenderId = lenderUid, amount = amount, type = "penalty",
            description = "₩$amount penalty received from ${record.borrowerName} for ${record.itemName}",
            timestamp = now
        )
        db.child("lender_credit_history/$lenderUid/${creditLog.entryId}").setValue(creditLog)

        val paymentLog = BorrowerPaymentHistory(
            entryId = db.child("borrower_payment_history/$borrowerUid").push().key ?: "",
            borrowerId = borrowerUid, amount = amount, type = "penalty",
            description = "₩$amount penalty paid for late return of ${record.itemName}",
            timestamp = now
        )
        db.child("borrower_payment_history/$borrowerUid/${paymentLog.entryId}").setValue(paymentLog)
    }

    suspend fun sendReminder(record: BorrowRecord): Result<Unit> = runCatching {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateStr = if (record.deadline > 0L) sdf.format(Date(record.deadline)) else "—"
        sendNotification(record.borrowerId, "Return Reminder", "Please return ${record.itemName} soon. Deadline: $dateStr", "reminder")
    }

    private suspend fun sendNotification(uid: String, title: String, body: String, type: String) {
        val id = db.child("notifications/$uid").push().key ?: ""
        val n = AppNotification(
            notifId = id,
            recipientId = uid,
            title = title,
            body = body,
            type = type,
            read = false,
            createdAt = System.currentTimeMillis()
        )
        db.child("notifications/$uid/$id").setValue(n)
    }

    suspend fun markNotificationsRead(uid: String) = runCatching {
        val ref = db.child("notifications/$uid")
        val snap = ref.get().await()
        val updates = hashMapOf<String, Any?>()
        snap.children.forEach { notifSnap ->
            val notif = notifSnap.getValue(AppNotification::class.java)
            if (notif != null && !notif.read) {
                updates["${notifSnap.key}/read"] = true
            }
        }
        if (updates.isNotEmpty()) ref.updateChildren(updates).await()
    }

    suspend fun getAllActive(): List<BorrowRecord> {
        val s = db.child("borrow_records").get().await()
        return s.children.mapNotNull { it.getValue(BorrowRecord::class.java) }
    }

    // ── Real-time Observers ──────────────────────────────────
    fun observeWallet(uid: String): Flow<Long> = callbackFlow {
        val ref = db.child("wallets/$uid/balance")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.getValue(Long::class.java) ?: 0L) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }

    fun observeBorrowerActive(uid: String): Flow<List<BorrowRecord>> = callbackFlow {
        val q = db.child("borrow_records").orderByChild("borrowerId").equalTo(uid)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(BorrowRecord::class.java) }) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observePendingReturns(lenderId: String): Flow<List<ReturnRequest>> = callbackFlow {
        val q = db.child("return_requests").orderByChild("lenderId").equalTo(lenderId)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(ReturnRequest::class.java) }) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val q = db.child("notifications/$uid").orderByChild("createdAt")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(AppNotification::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeLenderCreditHistory(uid: String): Flow<List<LenderCreditHistory>> = callbackFlow {
        val q = db.child("lender_credit_history/$uid").orderByChild("timestamp")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(LenderCreditHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeBorrowerPaymentHistory(uid: String): Flow<List<BorrowerPaymentHistory>> = callbackFlow {
        val q = db.child("borrower_payment_history/$uid").orderByChild("timestamp")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(BorrowerPaymentHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeLendHistory(uid: String): Flow<List<LendHistory>> = callbackFlow {
        val q = db.child("lend_history/$uid").orderByChild("returnedAt")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(LendHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    fun observeBorrowHistory(uid: String): Flow<List<BorrowHistory>> = callbackFlow {
        val q = db.child("borrow_history/$uid").orderByChild("returnedAt")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.getValue(BorrowHistory::class.java) }.reversed()) }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        q.addValueEventListener(l); awaitClose { q.removeEventListener(l) }
    }

    // ── NEW: Methods for searching and filtering all available items (Borrower Side) ──
    fun observeAllAvailableItems(): Flow<List<Item>> = callbackFlow {
        val query = db.child("items").orderByChild("status").equalTo("available")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                try {
                    val items = s.children.mapNotNull { it.getValue(Item::class.java) }
                    trySend(items)
                } catch (e: Exception) {
                    // Log or handle deserialization error
                }
            }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) {
                    // Try to send an empty list instead of closing, to keep UI alive
                    trySend(emptyList())
                } else {
                    close(e.toException())
                }
            }
        }
        query.addValueEventListener(l)
        awaitClose { query.removeEventListener(l) }
    }

    fun observeSystemCategories(): Flow<List<String>> = callbackFlow {
        val ref = db.child("items")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val cats = s.children.mapNotNull { it.getValue(Item::class.java) }
                    .filter { it.status == "available" }
                    .map { it.category }
                    .distinct()
                    .sorted()
                trySend(cats)
            }
            override fun onCancelled(e: DatabaseError) {
                if (e.code == DatabaseError.PERMISSION_DENIED) close() else close(e.toException())
            }
        }
        ref.addValueEventListener(l); awaitClose { ref.removeEventListener(l) }
    }
}
