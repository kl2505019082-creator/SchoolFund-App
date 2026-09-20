package com.uptm.schoolfund.model

/**
 * Represents a request from a teacher to the school finance department for
 * additional funding or approval of a specific spending need.
 * Stored in Firebase Firestore so both the teacher and finance staff see live status.
 */
data class FundRequest(
    var id: String = "",
    val teacherName: String = "",
    val teacherUid: String = "",
    val eventId: Long? = null,      // optional: link to a specific event budget
    val eventName: String = "",     // denormalised for easy display
    val amount: Double = 0.0,
    val reason: String = "",
    val status: String = "pending", // "pending", "approved", "rejected", "disbursed"
    val financeRemarks: String = "",
    val requestedAt: Long = System.currentTimeMillis(),
    val decidedAt: Long? = null,
    val decidedBy: String = "", // name of the Pengerusi PIBG who approved/rejected
    val disbursedAt: Long? = null,
    val disbursedBy: String = "", // name of the Bendahari PIBG who paid out the funds
    val workPaperBase64: String = "", // mandatory "kertas kerja" PDF (title, objectives, planned
    val workPaperFileName: String = "" // spending), stored inline since Firestore has no file storage here
)
