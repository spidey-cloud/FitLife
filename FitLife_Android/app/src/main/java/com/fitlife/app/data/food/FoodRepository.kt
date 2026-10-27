package com.fitlife.app.data.food

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * A food that can be logged.
 *
 * Nutrition values are either **per 100 g** ([per100g] = true, e.g. "Chicken Breast (grilled, 100g)"
 * and all Open Food Facts results) or **per listed portion** ([per100g] = false, e.g.
 * "Egg (whole, 1 large)", "Milk (whole, 250ml)"). The UI scales by grams for the former and by
 * number of portions for the latter. Before v2 every food was scaled by grams, which gave
 * wrong numbers for portion-based foods.
 */
data class FoodItem(
    val name: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val servingG: Float = 100f,
    val brand: String = "",
    val source: String = "local"   // "local" | "api"
) {
    val per100g: Boolean
        get() = source == "api" || PER_100G.containsMatchIn(name)

    /** Human label of the base portion: "100 g", "1 large", "250ml", "1 scoop 30g"… */
    val portionLabel: String
        get() = if (per100g) "100 g"
        else PORTION.find(name)?.groupValues?.get(1)?.substringAfterLast(", ")?.trim() ?: "1 serving"

    /**
     * Name without the portion part: "Tuna (canned in water, 100g)" → "Tuna (canned in water)",
     * "Egg White (1 large)" → "Egg White".
     */
    val displayName: String
        get() {
            if (source == "api") return name
            val m = PORTION.find(name) ?: return name
            val inner = m.groupValues[1]
            val head = name.substring(0, m.range.first).trimEnd()
            return if (inner.contains(", ")) "$head (${inner.substringBeforeLast(", ")})" else head.ifBlank { name }
        }

    private companion object {
        val PER_100G = Regex("""\b100\s?g\b""", RegexOption.IGNORE_CASE)
        val PORTION = Regex("""\(([^()]*)\)\s*$""")
    }
}

/** Result of an online search; [failed] lets the UI show an "offline" hint instead of "no results". */
data class ApiSearchResult(val items: List<FoodItem>, val failed: Boolean)

@Singleton
class FoodRepository @Inject constructor() {

    // ── Large local food database (100 common foods) ──────────────────────────
    private val localFoods: List<FoodItem> = listOf(
        // ── Proteins ──────────────────────────────────────────────────────────
        FoodItem("Chicken Breast (grilled, 100g)",    165, 31.0f, 0.0f,  3.6f),
        FoodItem("Chicken Thigh (cooked, 100g)",      209, 26.0f, 0.0f, 11.0f),
        FoodItem("Chicken Drumstick (100g)",          172, 28.3f, 0.0f,  5.7f),
        FoodItem("Salmon (baked, 100g)",              208, 20.0f, 0.0f, 13.0f),
        FoodItem("Tuna (canned in water, 100g)",      116, 25.5f, 0.0f,  1.0f),
        FoodItem("Tuna (canned in oil, 100g)",        198, 23.0f, 0.0f, 11.0f),
        FoodItem("Egg (whole, 1 large)",               78,  6.3f, 0.6f,  5.3f),
        FoodItem("Egg White (1 large)",                17,  3.6f, 0.2f,  0.1f),
        FoodItem("Egg Yolk (1 large)",                 55,  2.7f, 0.6f,  4.5f),
        FoodItem("Beef Mince (lean, 100g)",           215, 26.0f, 0.0f, 12.0f),
        FoodItem("Beef Steak (sirloin, 100g)",        207, 26.0f, 0.0f, 11.0f),
        FoodItem("Pork Loin (100g)",                  182, 25.0f, 0.0f,  8.7f),
        FoodItem("Turkey Breast (100g)",              135, 30.0f, 0.0f,  1.0f),
        FoodItem("Shrimp (100g)",                      99, 24.0f, 0.2f,  0.3f),
        FoodItem("Tofu (firm, 100g)",                  76,  8.1f, 1.9f,  4.8f),
        FoodItem("Tempeh (100g)",                     195, 19.0f, 7.6f, 11.0f),
        FoodItem("Greek Yogurt (full fat, 100g)",     97,  9.0f, 3.8f,  5.0f),
        FoodItem("Greek Yogurt (0% fat, 100g)",       59, 10.2f, 3.6f,  0.4f),
        FoodItem("Cottage Cheese (100g)",              98, 11.1f, 3.4f,  4.3f),
        FoodItem("Whey Protein (1 scoop 30g)",        120, 25.0f, 3.0f,  1.5f),

        // ── Dairy ─────────────────────────────────────────────────────────────
        FoodItem("Milk (whole, 250ml)",               149,  8.0f,12.0f,  8.0f),
        FoodItem("Milk (skimmed, 250ml)",              83,  8.3f,12.2f,  0.2f),
        FoodItem("Cheddar Cheese (30g)",              121,  7.4f, 0.1f, 10.0f),
        FoodItem("Mozzarella (30g)",                   85,  6.3f, 0.6f,  6.3f),
        FoodItem("Butter (10g)",                       72,  0.1f, 0.0f,  8.1f),
        FoodItem("Cream Cheese (30g)",                  89,  1.9f, 1.0f,  8.7f),

        // ── Grains & Carbs ────────────────────────────────────────────────────
        FoodItem("White Rice (cooked, 100g)",         130,  2.7f,28.0f,  0.3f),
        FoodItem("Brown Rice (cooked, 100g)",         111,  2.6f,23.0f,  0.9f),
        FoodItem("Basmati Rice (cooked, 100g)",       121,  3.5f,25.2f,  0.4f),
        FoodItem("Oats (rolled, dry, 50g)",           190,  6.5f,32.0f,  3.5f),
        FoodItem("Oats (instant, dry, 40g)",          148,  5.0f,26.5f,  2.8f),
        FoodItem("Pasta (white, cooked, 100g)",       158,  5.8f,31.0f,  0.9f),
        FoodItem("Pasta (wholemeal, cooked, 100g)",   149,  5.6f,29.0f,  0.9f),
        FoodItem("Bread (white, 1 slice 30g)",         79,  2.7f,15.0f,  1.0f),
        FoodItem("Bread (wholemeal, 1 slice 35g)",     83,  4.0f,14.5f,  1.1f),
        FoodItem("Bread (sourdough, 1 slice 50g)",    120,  4.5f,23.0f,  0.8f),
        FoodItem("Tortilla (flour, 1 medium)",        146,  3.8f,25.0f,  3.5f),
        FoodItem("Quinoa (cooked, 100g)",             120,  4.4f,21.3f,  1.9f),
        FoodItem("Potato (baked, 100g)",               93,  2.5f,21.0f,  0.1f),
        FoodItem("Sweet Potato (baked, 100g)",         86,  1.6f,20.0f,  0.1f),
        FoodItem("Corn (100g)",                        86,  3.3f,19.0f,  1.4f),

        // ── Fruits ────────────────────────────────────────────────────────────
        FoodItem("Apple (medium, 182g)",               95,  0.5f,25.0f,  0.3f),
        FoodItem("Banana (medium, 118g)",             105,  1.3f,27.0f,  0.4f),
        FoodItem("Orange (medium, 130g)",              62,  1.2f,15.4f,  0.2f),
        FoodItem("Strawberries (100g)",                32,  0.7f, 7.7f,  0.3f),
        FoodItem("Blueberries (100g)",                 57,  0.7f,14.5f,  0.3f),
        FoodItem("Grapes (100g)",                      69,  0.6f,18.0f,  0.2f),
        FoodItem("Mango (100g)",                       60,  0.8f,15.0f,  0.4f),
        FoodItem("Pineapple (100g)",                   50,  0.5f,13.0f,  0.1f),
        FoodItem("Watermelon (100g)",                  30,  0.6f, 7.6f,  0.2f),
        FoodItem("Avocado (half, 68g)",               114,  1.3f, 6.0f, 10.5f),
        FoodItem("Kiwi (1 medium, 76g)",               46,  0.9f,11.0f,  0.4f),

        // ── Vegetables ────────────────────────────────────────────────────────
        FoodItem("Broccoli (100g)",                    34,  2.8f, 7.0f,  0.4f),
        FoodItem("Spinach (100g)",                     23,  2.9f, 3.6f,  0.4f),
        FoodItem("Kale (100g)",                        49,  4.3f, 8.8f,  0.9f),
        FoodItem("Carrot (100g)",                      41,  0.9f,10.0f,  0.2f),
        FoodItem("Cucumber (100g)",                    16,  0.7f, 3.6f,  0.1f),
        FoodItem("Tomato (100g)",                      18,  0.9f, 3.9f,  0.2f),
        FoodItem("Onion (100g)",                       40,  1.1f, 9.3f,  0.1f),
        FoodItem("Bell Pepper (100g)",                 31,  1.0f, 6.0f,  0.3f),
        FoodItem("Mushroom (100g)",                    22,  3.1f, 3.3f,  0.3f),
        FoodItem("Zucchini (100g)",                    17,  1.2f, 3.1f,  0.3f),
        FoodItem("Cauliflower (100g)",                 25,  2.0f, 5.0f,  0.3f),

        // ── Legumes ───────────────────────────────────────────────────────────
        FoodItem("Lentils (cooked, 100g)",            116,  9.0f,20.0f,  0.4f),
        FoodItem("Chickpeas (cooked, 100g)",          164,  8.9f,27.0f,  2.6f),
        FoodItem("Black Beans (cooked, 100g)",        132,  8.9f,24.0f,  0.5f),
        FoodItem("Kidney Beans (cooked, 100g)",       127,  8.7f,22.8f,  0.5f),
        FoodItem("Edamame (100g)",                    122, 11.9f, 8.9f,  5.2f),

        // ── Nuts & Seeds ──────────────────────────────────────────────────────
        FoodItem("Almonds (30g)",                     174,  6.0f, 6.1f, 15.0f),
        FoodItem("Walnuts (30g)",                     196,  4.6f, 4.1f, 19.6f),
        FoodItem("Cashews (30g)",                     163,  4.3f, 9.2f, 13.1f),
        FoodItem("Peanuts (30g)",                     166,  7.5f, 6.1f, 14.1f),
        FoodItem("Peanut Butter (30g)",               188,  7.0f, 7.0f, 16.0f),
        FoodItem("Almond Butter (30g)",               196,  6.7f, 6.0f, 18.0f),
        FoodItem("Chia Seeds (15g)",                   73,  2.5f, 6.3f,  4.6f),
        FoodItem("Flaxseed (15g)",                     75,  2.6f, 4.1f,  6.0f),
        FoodItem("Sunflower Seeds (30g)",              175,  5.8f, 5.8f, 15.3f),

        // ── Oils & Fats ───────────────────────────────────────────────────────
        FoodItem("Olive Oil (1 tbsp, 14g)",            119,  0.0f, 0.0f, 13.5f),
        FoodItem("Coconut Oil (1 tbsp, 14g)",          121,  0.0f, 0.0f, 13.6f),

        // ── Snacks & Processed ────────────────────────────────────────────────
        FoodItem("Dark Chocolate (30g)",               172,  1.9f,13.1f, 12.1f),
        FoodItem("Milk Chocolate (30g)",               161,  2.2f,17.4f,  9.0f),
        FoodItem("Protein Bar (1 bar, 60g)",           220, 20.0f,24.0f,  7.0f),
        FoodItem("Rice Cake (1 piece, 9g)",             35,  0.7f, 7.3f,  0.3f),
        FoodItem("Popcorn (air-popped, 30g)",          110,  3.1f,22.0f,  1.3f),
        FoodItem("Hummus (30g)",                        70,  2.0f, 5.4f,  5.2f),

        // ── Drinks ────────────────────────────────────────────────────────────
        FoodItem("Orange Juice (250ml)",               112,  1.7f,25.8f,  0.5f),
        FoodItem("Apple Juice (250ml)",                114,  0.3f,28.0f,  0.3f),
        FoodItem("Whole Milk (250ml)",                 149,  8.0f,12.0f,  8.0f),
        FoodItem("Soy Milk (250ml)",                    80,  6.3f, 4.0f,  4.0f),
        FoodItem("Oat Milk (250ml)",                   120,  3.0f,16.0f,  5.0f),
        FoodItem("Coconut Water (250ml)",               46,  1.7f,10.4f,  0.5f),
        FoodItem("Sports Drink (500ml)",               150,  0.0f,37.5f,  0.0f),

        // ── Fast Food / Restaurant ────────────────────────────────────────────
        FoodItem("Pizza Margherita (1 slice, 107g)",   272,  9.0f,33.6f, 10.4f),
        FoodItem("Cheeseburger (1 regular)",           303, 15.0f,23.0f, 13.0f),
        FoodItem("French Fries (medium, 117g)",        365,  4.1f,48.0f, 17.0f),
        FoodItem("Fried Rice (100g)",                  163,  3.4f,27.6f,  4.5f),
        FoodItem("Sushi Roll (6 pieces, 150g)",        350, 12.0f,50.0f,  8.0f),
    )

    // ── Search local DB ───────────────────────────────────────────────────────

    fun searchLocal(query: String): List<FoodItem> {
        if (query.length < 2) return emptyList()
        val q = query.lowercase().trim()
        // Prioritise starts-with, then contains
        val startsWith = localFoods.filter { it.name.lowercase().startsWith(q) }
        val contains   = localFoods.filter { !it.name.lowercase().startsWith(q) && it.name.lowercase().contains(q) }
        return (startsWith + contains).take(8)
    }

    // ── Open Food Facts API ───────────────────────────────────────────────────
    // Searches the free Open Food Facts public API (no key needed).
    //
    // Hardening (v2):
    //  • HTTPS only — the connection is rejected if it is not an HttpsURLConnection
    //  • connect/read timeouts so a slow or hostile server can't hang the search forever
    //  • response size capped at 1 MB to avoid memory exhaustion
    //  • query trimmed, control characters stripped and length-limited before encoding
    //  • identifying User-Agent as requested by the Open Food Facts API terms
    //  • only the exception class is logged (never the query, which may be personal)

    suspend fun searchApi(query: String): List<FoodItem> = searchApiDetailed(query).items

    suspend fun searchApiDetailed(query: String): ApiSearchResult = withContext(Dispatchers.IO) {
        val q = sanitizeQuery(query)
        if (q.length < 3) return@withContext ApiSearchResult(emptyList(), failed = false)
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val url = URL(
                "https://world.openfoodfacts.org/cgi/search.pl" +
                    "?search_terms=$encoded" +
                    "&search_simple=1" +
                    "&action=process" +
                    "&json=1" +
                    "&page_size=8" +
                    "&fields=product_name,brands,nutriments,serving_size"
            )
            conn = (url.openConnection() as? HttpsURLConnection)
                ?: throw SecurityException("Refusing non-HTTPS connection")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            // Same-protocol redirects only: HttpURLConnection never follows HTTPS → HTTP.
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK)
                return@withContext ApiSearchResult(emptyList(), failed = true)
            val body = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_RESPONSE_BYTES) throw IllegalStateException("Response too large")
                    out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }
            ApiSearchResult(parseApiResponse(body), failed = false)
        } catch (e: Exception) {
            Log.w("FoodRepo", "API search failed: ${e.javaClass.simpleName}")
            ApiSearchResult(emptyList(), failed = true)
        } finally {
            conn?.disconnect()
        }
    }

    private fun sanitizeQuery(query: String): String =
        query.filterNot { it.isISOControl() }.trim().take(MAX_QUERY_LENGTH)

    private fun parseApiResponse(json: String): List<FoodItem> {
        // Values coming from the network are clamped so a malformed record can't poison totals.
        return try {
            val root = JSONObject(json)
            val products = root.getJSONArray("products")
            val results = mutableListOf<FoodItem>()
            for (i in 0 until products.length()) {
                val p = products.getJSONObject(i)
                val name = p.optString("product_name", "").trim().take(80)
                val brand = p.optString("brands", "").trim().substringBefore(",").take(40)
                val n = p.optJSONObject("nutriments") ?: continue
                val cal = n.optDouble("energy-kcal_100g", -1.0)
                if (name.isBlank() || cal < 0 || cal > 900 || cal.isNaN()) continue
                results.add(
                    FoodItem(
                        name    = if (brand.isNotEmpty()) "$name ($brand)" else name,
                        calories = cal.toInt(),
                        proteinG = n.optDouble("proteins_100g", 0.0).clampMacro(),
                        carbsG   = n.optDouble("carbohydrates_100g", 0.0).clampMacro(),
                        fatG     = n.optDouble("fat_100g", 0.0).clampMacro(),
                        servingG = 100f,
                        brand    = brand,
                        source   = "api"
                    )
                )
            }
            results
        } catch (e: Exception) { emptyList() }
    }

    // ── Combined search: local first (instant), then API ─────────────────────

    fun searchLocalInstant(query: String) = searchLocal(query)

    suspend fun searchCombined(query: String): List<FoodItem> {
        val local = searchLocal(query)
        val api   = searchApi(query)
        // Merge, deduplicate by name prefix
        val merged = (local + api).distinctBy { it.name.lowercase().take(20) }
        return merged.take(10)
    }

    private fun Double.clampMacro(): Float =
        if (isNaN() || this < 0) 0f else coerceAtMost(100.0).toFloat()

    private companion object {
        const val TIMEOUT_MS = 8_000
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val MAX_QUERY_LENGTH = 100
        const val USER_AGENT = "FitLife-Android/1.0 (personal fitness app)"
    }
}
