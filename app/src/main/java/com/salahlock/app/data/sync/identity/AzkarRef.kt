package com.salahlock.app.data.sync.identity

/**
 * BM-013 Checkpoint B — deterministic, cross-device stable identity for a single
 * Azkar entry.
 *
 * ## Why this exists
 * The bundled corpus (`assets/azkar.json`) has **no** stable per-entry identifier,
 * and the local Room `azkar_table.id` is an autoincrement `Int` assigned in seed
 * order — it is NOT stable across a reinstall, DB recreation, or corpus re-seed.
 * A user's azkar bookmark must resolve to the *same* logical entry on every device,
 * so we derive a stable reference purely from **immutable source structure**:
 *
 *   `azkar:v1:<normalized-category>:<index-within-category>`
 *
 * where *index-within-category* is the 0-based ordinal of the entry among all
 * entries sharing its category, in the canonical `azkar.json` array order.
 *
 * ## Determinism guarantees (see AzkarRefTest)
 * The same logical azkar resolves to the same ref across: a different device, a
 * reinstall, a DB recreation, and a corpus re-seed — because it depends only on
 * `(category, canonical order)`, never on:
 *   - translated / display text
 *   - transliteration or reference strings
 *   - whitespace-sensitive content
 *   - the autoincrement row id
 *
 * ## Identity-update contract (review item 6) — READ BEFORE EDITING azkar.json
 * Because identity is `(category, ordinal-within-category)`, the canonical ordering
 * of `azkar.json` is **identity-sensitive** under `v1`:
 *  - APPENDING a new entry at the END of its category is safe (new ordinal only).
 *  - INSERTING or REORDERING an entry in the MIDDLE of a category shifts every later
 *    ordinal → silently repoints existing user bookmarks to the wrong azkar. DO NOT
 *    do this without a versioned identity migration.
 *  - RENAMING a category also changes the ref for all its entries.
 * If any such change is unavoidable, bump [VERSION] to `v2` and ship a v1→v2 remap;
 * never silently redefine `v1`. Longer term, prefer adding explicit immutable ids to
 * the bundled dataset so identity no longer depends on ordering at all.
 */
object AzkarRef {

    const val VERSION: String = "v1"

    /** Builds the stable ref from a category and its 0-based index within that category. */
    fun of(category: String, indexWithinCategory: Int): String {
        require(indexWithinCategory >= 0) { "indexWithinCategory must be >= 0" }
        return "azkar:$VERSION:${normalizeCategory(category)}:$indexWithinCategory"
    }

    /**
     * Assigns a stable ref to every entry in a corpus list presented in **canonical
     * order** (i.e. `azkar.json` array order, which is also ascending `azkar_table.id`
     * order). Returns refs positionally aligned to [categoriesInCanonicalOrder].
     *
     * This is the single source of truth used by BOTH the seeder and the v8→v9
     * migration, so a freshly-seeded corpus and a migrated legacy corpus produce
     * byte-identical refs for the same entry.
     */
    fun assign(categoriesInCanonicalOrder: List<String>): List<String> {
        val perCategoryCount = HashMap<String, Int>()
        return categoriesInCanonicalOrder.map { category ->
            val key = normalizeCategory(category)
            val index = perCategoryCount.getOrDefault(key, 0)
            perCategoryCount[key] = index + 1
            of(category, index)
        }
    }

    /**
     * Category normalization: trim, lowercase, collapse internal whitespace to `_`.
     * Deliberately conservative — categories are stable control strings in the
     * asset ("Morning", "After Prayer", …), not user/display text.
     */
    fun normalizeCategory(category: String): String =
        category.trim().lowercase().replace(Regex("\\s+"), "_")
}
