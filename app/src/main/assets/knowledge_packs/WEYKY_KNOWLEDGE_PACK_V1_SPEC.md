# WEYKY_KNOWLEDGE_PACK_V1 — Official Format & Importer Specification

## 1. Overview
`WEYKY_KNOWLEDGE_PACK_V1` is the official offline-first JSON format for importing external knowledge packs into WEYKY's local Room + `InMemorySearchSnapshot` intelligence architecture.

The importer (`KnowledgePackImporter`) is engineered around five non-negotiable guarantees:
1. **Strict Streaming Validation (`Dispatchers.IO`):** Uses `com.squareup.moshi.JsonReader` over a byte-bounded SHA-256 stream (`BoundedSha256InputStream`), enforcing size, depth, duplicate-key, and strict token-type checks before any database write.
2. **Core Seed Protection (`CORE_PROTECTION_VIOLATION`):** External packs can never overwrite, shadow, or modify `core_seed_pack` (`SEED_BUILTIN`) or any of the 11 protected core seed topics (`SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS`).
3. **Deterministic Idempotency & Version Resolution:** Detects identical re-imports (`ALREADY_IMPORTED`), upgrades (`SUCCESS_UPDATED`), downgrades (`DUPLICATE_CONFLICT`), and same-version content drift (`DUPLICATE_CONFLICT`).
4. **Atomic Persistence & Snapshot Publication:** Writes pack metadata, topic headers, normalized keywords, keyword tokens, lazy-loaded details, and follow-up branches inside a single `WeykyKnowledgeDatabase.withTransaction` block, then refreshes `InMemorySearchSnapshot` via `<= 3` bulk `SELECT` queries.
5. **100% Lazy Loading Preservation:** Importing `10`, `100`, `500`, or `1,000` topics loads `0` topic details into `KnowledgeCache.detailCache`. Detailed explanations and follow-up branches remain in SQLite until requested by `LocalConversationalEngine` / `KnowledgeRepository.getOrLoadTopicDetail()`.

---

## 2. Top-Level JSON Schema (`WEYKY_KNOWLEDGE_PACK_V1`)

| Field | Type | Required | Constraints |
|---|---|---|---|
| `packFormat` | `String` | Optional (defaults to `"WEYKY_KNOWLEDGE_PACK_V1"`) | Must equal `"WEYKY_KNOWLEDGE_PACK_V1"` (`SCHEMA_UNSUPPORTED` otherwise) |
| `schemaVersion` | `Int` | **Required** | Must equal `1` (`SCHEMA_UNSUPPORTED` otherwise) |
| `packId` | `String` | **Required** | `^[a-zA-Z0-9_.-]{1,128}$`; must not equal `"core_seed_pack"` |
| `packName` | `String` | Optional (defaults to `packId`) | Non-blank string, max `256` chars |
| `packVersion` | `Int` | **Required** | Integer `>= 1` |
| `contentVersion` | `Int` | Optional (defaults to `packVersion`) | Integer `>= 1` |
| `sourceType` | `String` | Optional (defaults to `"LOCAL_UPDATE"`) | `"BUNDLED_ASSET"` or `"LOCAL_UPDATE"` (`"SEED_BUILTIN"` is forbidden for external packs) |
| `packPriority` | `Int` | **Required** | Integer `>= 0` (Seed builtin uses `1000`) |
| `description` | `String` | Optional | Max `2,000` chars |
| `language` | `String` | Optional (defaults to `"bilingual"`) | One of `"ar"`, `"en"`, `"bilingual"`, `"ar-en"`, `"mixed"` |
| `createdAt` | `Long` | Optional | Positive epoch timestamp (`ms`) |
| `updatedAt` | `Long` | Optional | Epoch timestamp `>= createdAt` |
| `contentSha256` | `String?` | Optional | 64-char hex SHA-256 matching canonical logical SHA-256 (or raw SHA-256) |
| `topics` | `Array<Topic>` | **Required** | `1 .. 2,000` topics |

---

## 3. Topic Object Schema

| Field | Type | Required | Constraints |
|---|---|---|---|
| `topicId` | `String` | **Required** | `^[a-zA-Z0-9_.-]{1,128}$`; unique within pack; must not be in `PROTECTED_CORE_TOPIC_IDS` |
| `title` | `String` | Optional | Max `256` chars |
| `arabicTitle` | `String` | Optional | Max `256` chars |
| `englishTitle` | `String` | Optional | Max `256` chars |
| `category` | `String` | Optional (defaults to `"general"`) | Max `128` chars |
| `topicOrderIndex` | `Int` | Optional (defaults to array index) | Integer `>= 0`; unique within pack |
| `priority` | `Int` | Optional (defaults to `100`) | Integer `>= 0` |
| `contentVersion` | `Int` | Optional (defaults to `1`) | Integer `>= 1` |
| `arabicSummary` | `String` | **Required** | Non-blank, max `4,000` chars |
| `englishSummary` | `String` | **Required** | Non-blank, max `4,000` chars |
| `keywords` | `Array<String>` | **Required** | `1 .. 64` non-blank strings; no duplicate normalized phrases within the same topic |
| `suggestedFollowUps` | `Array<String>` | **Required** | `1 .. 12` non-blank follow-up questions |
| `referenceTopicIds` | `Array<String>` | Optional | `0 .. 32` valid active topic IDs (no self-reference, no duplicate refs, no inactive/unknown targets) |
| `detail` | `TopicDetail?` | Optional | Stored in `knowledge_topic_details` and lazy-loaded on demand |
| `followUpBranches` | `Array<Branch>` | Optional | `0 .. 20` branches stored in `knowledge_followup_branches` and lazy-loaded on demand |

---

## 4. SHA-256 Calculation Rules
1. **Raw Byte-Level SHA-256 (`rawBytesSha256`):** Computed directly over the incoming UTF-8 byte stream by `BoundedSha256InputStream` during streaming parse without re-serializing JSON.
2. **Canonical Logical Content SHA-256 (`logicalContentSha256`):** Computed deterministically by `KnowledgePackSpecificationV1.computeLogicalContentSha256(pack)` across all semantic fields in canonical order while **excluding** `contentSha256` so an embedded checksum never causes circular self-hashing.
3. **Verification:**
   - If `expectedSha256` is passed to the importer, it must match either `rawBytesSha256` or `logicalContentSha256` (`HASH_MISMATCH` otherwise).
   - If `contentSha256` is embedded in the JSON root object, it must match `logicalContentSha256` (or `rawBytesSha256`).
