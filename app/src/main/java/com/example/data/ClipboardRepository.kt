package com.example.data

import kotlinx.coroutines.flow.Flow

class ClipboardRepository(private val clipboardDao: ClipboardDao) {

    val allItems: Flow<List<ClipboardItem>> = clipboardDao.getAllItems()

    fun searchItems(query: String): Flow<List<ClipboardItem>> {
        return if (query.isBlank()) {
            clipboardDao.getAllItems()
        } else {
            clipboardDao.searchItems(query)
        }
    }

    /**
     * Stores a copied text using smart deduplication.
     * If the text already exists, we update its timestamp to bring it to the top.
     * Otherwise, we insert a new item.
     */
    suspend fun saveCopiedText(text: String) {
        if (text.isBlank()) return
        
        val trimmedText = text.trim()
        val existingItem = clipboardDao.getItemByText(trimmedText)
        if (existingItem != null) {
            val updated = existingItem.copy(timestamp = System.currentTimeMillis())
            clipboardDao.updateItem(updated)
        } else {
            val newItem = ClipboardItem(text = trimmedText, timestamp = System.currentTimeMillis())
            clipboardDao.insertItem(newItem)
        }
    }

    suspend fun togglePin(item: ClipboardItem) {
        val updated = item.copy(isPinned = !item.isPinned)
        clipboardDao.updateItem(updated)
    }

    suspend fun deleteItem(item: ClipboardItem) {
        clipboardDao.deleteItem(item)
    }

    suspend fun deleteItemById(id: Int) {
        clipboardDao.deleteItemById(id)
    }

    suspend fun clearAll() {
        clipboardDao.clearAll()
    }
}
