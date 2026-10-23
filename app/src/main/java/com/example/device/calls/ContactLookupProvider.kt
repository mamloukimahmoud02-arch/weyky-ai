package com.example.device.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale

data class ContactMatch(
  val id: String,
  val displayName: String,
  val phoneNumber: String
)

sealed interface ContactLookupResult {
  data class FoundSingle(val contact: ContactMatch) : ContactLookupResult
  data class FoundMultiple(val query: String, val contacts: List<ContactMatch>) : ContactLookupResult
  data class NotFound(val query: String) : ContactLookupResult
  object PermissionRequired : ContactLookupResult
}

class ContactLookupProvider(private val context: Context) {

  fun hasContactsPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
  }

  /**
   * Normalizes strings for robust matching across Arabic variants (alif, yaa, taa marbuta) and case-insensitivity.
   */
  private fun normalizeForSearch(text: String): String {
    return text.trim()
      .lowercase(Locale.ROOT)
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ؤ', 'و')
      .replace('ئ', 'ي')
      .replace("  ", " ")
  }

  fun lookupContact(query: String): ContactLookupResult {
    if (!hasContactsPermission()) {
      return ContactLookupResult.PermissionRequired
    }

    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      return ContactLookupResult.NotFound(query)
    }

    val searchCandidates = mutableListOf<String>()
    searchCandidates.add(cleanQuery)
    if (cleanQuery.startsWith("بـ") && cleanQuery.length > 2) {
      searchCandidates.add(cleanQuery.substring(2).trim())
    } else if (cleanQuery.startsWith("ب") && cleanQuery.length > 2) {
      searchCandidates.add(cleanQuery.substring(1).trim())
    }

    val matches = mutableListOf<ContactMatch>()
    var cursor: Cursor? = null
    try {
      val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
      val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
      )

      cursor = context.contentResolver.query(
        uri,
        projection,
        null,
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
      )

      cursor?.let {
        val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        val normalizedSearchCandidates = searchCandidates.map { c -> normalizeForSearch(c) }

        while (it.moveToNext()) {
          val id = if (idIdx >= 0) it.getString(idIdx) else ""
          val name = if (nameIdx >= 0) it.getString(nameIdx) else cleanQuery
          val num = if (numIdx >= 0) it.getString(numIdx) else ""

          if (num.isNotBlank()) {
            val normalizedName = normalizeForSearch(name)
            val isMatched = normalizedSearchCandidates.any { candidate ->
              normalizedName.contains(candidate) || candidate.contains(normalizedName)
            }
            if (isMatched) {
              val normalizedNum = num.replace(" ", "").replace("-", "")
              if (matches.none { m -> m.displayName.equals(name, ignoreCase = true) && m.phoneNumber == normalizedNum }) {
                matches.add(ContactMatch(id = id, displayName = name, phoneNumber = normalizedNum))
              }
            }
          }
        }
      }
    } catch (_: Exception) {
      return ContactLookupResult.NotFound(query)
    } finally {
      cursor?.close()
    }

    return when {
      matches.isEmpty() -> ContactLookupResult.NotFound(cleanQuery)
      matches.size == 1 -> ContactLookupResult.FoundSingle(matches.first())
      else -> {
        val exactMatches = matches.filter { m ->
          searchCandidates.any { c -> normalizeForSearch(m.displayName) == normalizeForSearch(c) }
        }
        if (exactMatches.size == 1) {
          ContactLookupResult.FoundSingle(exactMatches.first())
        } else {
          ContactLookupResult.FoundMultiple(cleanQuery, matches)
        }
      }
    }
  }
}
