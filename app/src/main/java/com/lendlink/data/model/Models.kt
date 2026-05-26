package com.lendlink.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── User ─────────────────────────────────────────────────────
data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "",          // "lender" or "borrower"
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationAddress: String = "",
    val profileImageUrl: String = "", // Added profile image support
    val createdAt: Long = 0L
)

// ── Wallet ────────────────────────────────────────────────────
data class Wallet(
    val userId: String = "",
    val balance: Long = 0L,
    val lastUpdated: Long = 0L
)

// ── Category ──────────────────────────────────────────────────
data class Category(
    val categoryId: String = "",
    val name: String = "",
    val lenderId: String = "",
    val createdAt: Long = 0L
)

// ── Item ──────────────────────────────────────────────────────
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val itemId: String = "",
    val lenderId: String = "",
    val lenderName: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val lenderLatitude: Double = 0.0,
    val lenderLongitude: Double = 0.0,
    val name: String = "",
    val description: String = "",
    val price: Long = 0L,
    val category: String = "",
    val imageUrl: String = "",
    val status: String = "available",   // "available" or "lent"
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val recordId: String = "",
    val damageReport: DamageReport? = null,
    val deadline: Long = 0L,
    val borrowedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toRecord(manualRecordId: String = "") = BorrowRecord(
        recordId = if (manualRecordId.isNotEmpty()) manualRecordId else this.recordId,
        itemId = this.itemId,
        itemName = this.name,
        itemImageUrl = this.imageUrl,
        itemCategory = this.category,
        lenderId = this.lenderId,
        lenderName = this.lenderName,
        lenderPhone = this.lenderPhone,
        lenderLocation = this.lenderLocation,
        borrowerId = this.borrowerId,
        borrowerName = this.borrowerName,
        borrowerPhone = this.borrowerPhone,
        borrowerLocation = this.borrowerLocation,
        price = this.price,
        borrowedAt = this.borrowedAt,
        deadline = this.deadline,
        status = this.status,
        damageReport = this.damageReport
    )
}

// ── BorrowRecord ──────────────────────────────────────────────
@Entity(tableName = "borrow_records")
data class BorrowRecord(
    @PrimaryKey val recordId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val lenderId: String = "",
    val lenderName: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val price: Long = 0L,
    val borrowedAt: Long = 0L,
    val deadline: Long = 0L,
    val returnedAt: Long = 0L,
    val status: String = "active",  // "active", "return_requested", "damaged", "negotiating", "returned"
    val penaltyAccrued: Long = 0L,
    val damageReport: DamageReport? = null
)

// ── Damage Report ─────────────────────────────────────────────
data class DamageReport(
    val condition: String = "", // "Minor", "Moderate", "Severe"
    val description: String = "",
    val chargeAmount: Long = 0L,
    val damageImageUrl: String = "",
    val reportedAt: Long = System.currentTimeMillis(),
    val status: String = "pending" // "pending", "negotiating", "paid", "resolved"
)

// ── Damage History ────────────────────────────────────────────
@Entity(tableName = "damage_history")
data class DamageHistory(
    @PrimaryKey val historyId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val lenderId: String = "",
    val lenderName: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val damageImageUrl: String = "",
    val condition: String = "",
    val description: String = "",
    val chargeAmount: Long = 0L,
    val borrowedAt: Long = 0L,
    val paymentStatus: String = "", // "Paid" or "Negotiated"
    val timestamp: Long = System.currentTimeMillis()
)

// ── CreditHistory (Lender) ────────────────────────────────────
@Entity(tableName = "lender_credit_history")
data class LenderCreditHistory(
    @PrimaryKey val entryId: String = "",
    val lenderId: String = "",
    val amount: Long = 0L,
    val type: String = "",           // "borrow_payment", "penalty", "damage_charge"
    val borrowerName: String = "",
    val itemName: String = "",
    val itemId: String = "",
    val recordId: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ── PaymentHistory (Borrower) ─────────────────────────────────
@Entity(tableName = "borrower_payment_history")
data class BorrowerPaymentHistory(
    @PrimaryKey val entryId: String = "",
    val borrowerId: String = "",
    val amount: Long = 0L,
    val type: String = "",           // "borrow_payment", "penalty", "damage_charge"
    val lenderName: String = "",
    val itemName: String = "",
    val itemId: String = "",
    val recordId: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)


// ── LendHistory (Lender) ──────────────────────────────────────
@Entity(tableName = "lend_history")
data class LendHistory(
    @PrimaryKey val historyId: String = "",
    val lenderId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val borrowerName: String = "",
    val borrowerId: String = "",
    val borrowerPhone: String = "",
    val borrowerLocation: String = "",
    val price: Long = 0L,
    val lentAt: Long = 0L,
    val returnedAt: Long = 0L,
    val recordId: String = ""
)

// ── BorrowHistory (Borrower) ──────────────────────────────────
@Entity(tableName = "borrow_history")
data class BorrowHistory(
    @PrimaryKey val historyId: String = "",
    val borrowerId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemImageUrl: String = "",
    val itemCategory: String = "",
    val lenderName: String = "",
    val lenderId: String = "",
    val lenderPhone: String = "",
    val lenderLocation: String = "",
    val price: Long = 0L,
    val borrowedAt: Long = 0L,
    val returnedAt: Long = 0L,
    val recordId: String = ""
)

// ── Notification ──────────────────────────────────────────────
data class AppNotification(
    val notifId: String = "",
    val recipientId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "",
    val itemId: String = "",
    val recordId: String = "",
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ── ReturnRequest ─────────────────────────────────────────────
data class ReturnRequest(
    val requestId: String = "",
    val recordId: String = "",
    val itemId: String = "",
    val borrowerId: String = "",
    val lenderId: String = "",
    val status: String = "pending",
    val requestedAt: Long = 0L
)

// ── Default categories (can be customised per lender) ─────────
val DEFAULT_CATEGORIES = listOf("Books", "Games", "Electronics", "Car", "Bike")
