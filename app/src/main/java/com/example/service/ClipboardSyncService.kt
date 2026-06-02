package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.example.data.ClipboardDatabase
import com.example.data.ClipboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardSyncService : AccessibilityService() {

    private var clipboardManager: ClipboardManager? = null
    private lateinit var repository: ClipboardRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        processClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        val database = ClipboardDatabase.getDatabase(applicationContext)
        repository = ClipboardRepository(database.clipboardDao())
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Proactively process current primary clip on connection
        processClipboard()
    }

    private fun processClipboard() {
        try {
            val manager = clipboardManager ?: return
            if (manager.hasPrimaryClip()) {
                val clip = manager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item.text?.toString()
                    if (!text.isNullOrBlank()) {
                        serviceScope.launch {
                            repository.saveCopiedText(text)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // Gracefully ignore clipboard access blocks from background thread on newer Android versions
        } catch (e: Exception) {
            // Catch other possible context/thread issues gracefully
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Gboard copies trigger primary clip updates. To capture history proactively,
        // we can also do a backup check on window changes or view clicks.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            processClipboard()
        }
    }

    override fun onInterrupt() {
        // Mandatory override for AccessibilityService
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
    }
}
