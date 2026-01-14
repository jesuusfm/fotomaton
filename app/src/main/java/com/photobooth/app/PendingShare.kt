package com.photobooth.app

data class PendingShare(
    val id: String,
    val eventName: String,
    val filePath: String,
    val fileUri: String,
    val fileType: String, // PHOTO or VIDEO
    val contact: String, // email or phone number
    val contactType: String, // EMAIL or PHONE
    val timestamp: Long,
    val sent: Boolean = false
)
