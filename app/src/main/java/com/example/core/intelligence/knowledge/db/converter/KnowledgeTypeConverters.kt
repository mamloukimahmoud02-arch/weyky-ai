package com.example.core.intelligence.knowledge.db.converter

import androidx.room.TypeConverter
import com.example.core.intelligence.conversation.ActionableOption
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Room TypeConverters and deterministic JSON serialization helpers for WEYKY's local knowledge
 * storage layer.
 *
 * Uses the existing [ActionableOption] class from `com.example.core.intelligence.conversation`
 * without creating any duplicate model.
 */
class KnowledgeTypeConverters {

    companion object {
        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val actionableOptionListType = Types.newParameterizedType(
            List::class.java,
            ActionableOption::class.java
        )
        private val actionableOptionListAdapter: JsonAdapter<List<ActionableOption>> =
            moshi.adapter(actionableOptionListType)

        private val stringListType = Types.newParameterizedType(
            List::class.java,
            String::class.java
        )
        private val stringListAdapter: JsonAdapter<List<String>> =
            moshi.adapter(stringListType)

        private val stringMapType = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            String::class.java
        )
        private val stringMapAdapter: JsonAdapter<Map<String, String>> =
            moshi.adapter(stringMapType)

        /**
         * Strictly validates whether [json] is a valid JSON array of [ActionableOption] objects
         * where every element has non-null `title` and `queryToExecute`.
         */
        fun isValidActionableOptionListJson(json: String?): Boolean {
            if (json == null || json.isBlank()) return false
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
            return try {
                val parsed = actionableOptionListAdapter.failOnUnknown().fromJson(trimmed) ?: return false
                parsed.all { option ->
                    option.title.isNotBlank() && option.queryToExecute.isNotBlank()
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Strictly validates whether [json] is a valid JSON array of non-null strings.
         */
        fun isValidStringListJson(json: String?, allowBlankElements: Boolean = false): Boolean {
            if (json == null || json.isBlank()) return false
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
            return try {
                val parsed = stringListAdapter.fromJson(trimmed) ?: return false
                if (allowBlankElements) true else parsed.all { it.isNotBlank() }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Strictly validates whether [json] (when non-null) is a valid JSON object (`{...}`).
         */
        fun isValidOptionalJsonObject(json: String?): Boolean {
            if (json == null) return true
            val trimmed = json.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
            return try {
                val adapter = moshi.adapter<Map<*, *>>(Map::class.java)
                adapter.fromJson(trimmed) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    @TypeConverter
    fun fromActionableOptionList(options: List<ActionableOption>?): String {
        if (options.isNullOrEmpty()) return "[]"
        return actionableOptionListAdapter.toJson(options)
    }

    @TypeConverter
    fun toActionableOptionList(json: String?): List<ActionableOption> {
        if (json.isNullOrBlank()) return emptyList()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        return try {
            actionableOptionListAdapter.fromJson(trimmed) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringList(items: List<String>?): String {
        if (items.isNullOrEmpty()) return "[]"
        return stringListAdapter.toJson(items)
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        return try {
            stringListAdapter.fromJson(trimmed) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? {
        if (map == null) return null
        if (map.isEmpty()) return "{}"
        return stringMapAdapter.toJson(map)
    }

    @TypeConverter
    fun toStringMap(json: String?): Map<String, String>? {
        if (json == null) return null
        val trimmed = json.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyMap()
        return try {
            stringMapAdapter.fromJson(trimmed) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
