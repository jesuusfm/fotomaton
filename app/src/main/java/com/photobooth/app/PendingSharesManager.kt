package com.photobooth.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PendingSharesManager(private val context: Context) {
    
    private val file = File(context.filesDir, "pending_shares.json")
    
    fun addPendingShare(
        eventName: String,
        filePath: String,
        fileUri: String,
        fileType: String,
        contact: String,
        contactType: String
    ): PendingShare {
        val share = PendingShare(
            id = UUID.randomUUID().toString(),
            eventName = eventName,
            filePath = filePath,
            fileUri = fileUri,
            fileType = fileType,
            contact = contact,
            contactType = contactType,
            timestamp = System.currentTimeMillis(),
            sent = false
        )
        
        val shares = getAllPendingShares().toMutableList()
        shares.add(share)
        saveShares(shares)
        
        return share
    }
    
    fun getAllPendingShares(): List<PendingShare> {
        if (!file.exists()) {
            return emptyList()
        }
        
        return try {
            val json = file.readText()
            val jsonArray = JSONArray(json)
            val shares = mutableListOf<PendingShare>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                shares.add(
                    PendingShare(
                        id = obj.getString("id"),
                        eventName = obj.getString("eventName"),
                        filePath = obj.getString("filePath"),
                        fileUri = obj.getString("fileUri"),
                        fileType = obj.getString("fileType"),
                        contact = obj.getString("contact"),
                        contactType = obj.getString("contactType"),
                        timestamp = obj.getLong("timestamp"),
                        sent = obj.getBoolean("sent")
                    )
                )
            }
            
            shares
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun getPendingSharesByEvent(eventName: String): List<PendingShare> {
        return getAllPendingShares().filter { it.eventName == eventName }
    }
    
    fun markAsSent(shareId: String) {
        val shares = getAllPendingShares().map {
            if (it.id == shareId) {
                it.copy(sent = true)
            } else {
                it
            }
        }
        saveShares(shares)
    }
    
    fun deletePendingShare(shareId: String) {
        val shares = getAllPendingShares().filter { it.id != shareId }
        saveShares(shares)
    }
    
    fun getPendingCount(eventName: String): Int {
        return getAllPendingShares().count { it.eventName == eventName && !it.sent }
    }
    
    private fun saveShares(shares: List<PendingShare>) {
        val jsonArray = JSONArray()
        shares.forEach { share ->
            val obj = JSONObject().apply {
                put("id", share.id)
                put("eventName", share.eventName)
                put("filePath", share.filePath)
                put("fileUri", share.fileUri)
                put("fileType", share.fileType)
                put("contact", share.contact)
                put("contactType", share.contactType)
                put("timestamp", share.timestamp)
                put("sent", share.sent)
            }
            jsonArray.put(obj)
        }
        
        file.writeText(jsonArray.toString())
    }
}
