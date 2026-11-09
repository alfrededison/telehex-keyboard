@file:Suppress("DEPRECATION")

package com.telehex.keyboard.data

import android.content.Context
import android.test.InstrumentationTestCase
import com.telehex.keyboard.layout.CellGrid
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.layout.KeyLayers
import com.telehex.keyboard.layout.LayoutModel
import com.telehex.keyboard.layout.Profile
import com.telehex.keyboard.layout.SymbolPages
import org.json.JSONArray
import org.json.JSONObject

/** Chuyển dữ liệu đã lưu sang bố cục hiện tại, trên SharedPreferences thật (file riêng, không đụng dữ liệu app). */
class PrefsMigrationTest : InstrumentationTestCase() {
    private val file = "telehex-migration-test"

    /** Bố cục mặc định đến 0.7.0 cùng hai lớp mặc định của nó. */
    private val legacy = LayoutModel(
        listOf(
            listOf("w", "r", "y", KeyCode.SETTINGS, "u", "o", KeyCode.NUMBERS),
            listOf("q", "e", "t", "f", "h", "i", "p"),
            listOf("a", "s", "d", "g", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m"),
            listOf(KeyCode.EMOJI, ",", null, null, null, ".", KeyCode.ENTER),
        )
    )
    private val legacySwipe = KeyLayers.pinDigits(CellGrid.fromCodes(legacy) { KeyLayers.DEFAULT_SWIPE_BY_CODE[it] })
    private val legacySymbols = SymbolPages.fromLetters(legacy).let { listOf(KeyLayers.pinDigits(it[0]), it[1]) }
    private val legacyProfile = Profile("", legacy, legacySwipe, legacySymbols)

    private fun raw() = instrumentation.targetContext.getSharedPreferences(file, Context.MODE_PRIVATE)

    private fun prefs() = Prefs(instrumentation.targetContext, file)

    override fun setUp() {
        super.setUp()
        raw().edit().clear().commit()
    }

    override fun tearDown() {
        raw().edit().clear().commit()
        super.tearDown()
    }

    private fun grids(g: List<CellGrid>) = JSONArray(g.map { grid -> JSONArray(grid.rows.map { JSONArray(it) }) }).toString()

    private fun profiles(vararg p: Profile) = JSONArray(p.map {
        JSONObject().put("name", it.name).put("letters", it.letters.toJson())
            .put("swipe", grids(listOf(it.swipe))).put("symbols", grids(it.symbols))
    }).toString()

    /** Ba lớp đang dùng như bản cũ đã ghi, kèm profile đang dùng (nếu có). */
    private fun storeLive(p: Profile, active: String?) {
        val e = raw().edit()
            .putString(Prefs.KEY_LAYOUT, p.letters.toJson())
            .putString(Prefs.KEY_SWIPE_GRID, grids(listOf(p.swipe)))
            .putString(Prefs.KEY_SYMBOL_PAGES, grids(p.symbols))
        if (active != null) e.putString(Prefs.KEY_ACTIVE_PROFILE, active)
        e.commit()
    }

    private fun assertLive(expected: Profile, p: Prefs) {
        assertEquals(expected.letters.rows, p.layout.rows)
        assertEquals(expected.swipe, p.swipeGrid)
        assertEquals(expected.symbols, p.symbolPages)
    }

    fun testFreshInstallUsesTheDefault() {
        val p = prefs()
        p.ensureProfiles()
        assertEquals(Profile.DEFAULT.name, p.activeProfile.name)
        assertTrue(p.userProfiles.isEmpty())
        assertLive(Profile.DEFAULT, p)
    }

    fun testDefaultUserMovesToTheNewDefault() {
        storeLive(legacyProfile, Profile.DEFAULT.name)
        val p = prefs()
        p.ensureProfiles()
        assertEquals(Profile.DEFAULT.name, p.activeProfile.name)
        assertTrue(p.userProfiles.isEmpty())
        assertLive(Profile.DEFAULT, p)
    }

    fun testRemovedBuiltInKeepsItsLayersAsMine() {
        storeLive(legacyProfile, "Cân bằng hai ngón")
        val p = prefs()
        p.ensureProfiles()
        assertEquals(Profile.MINE, p.activeProfile.name)
        assertTrue(p.userProfiles.single().sameLayers(legacyProfile))
        assertLive(legacyProfile, p)
    }

    fun testUserProfileIsLeftAlone() {
        val mine = legacyProfile.named("Long")
        storeLive(mine, mine.name)
        raw().edit().putString(Prefs.KEY_PROFILES, profiles(mine)).commit()
        val p = prefs()
        p.ensureProfiles()
        assertEquals(mine.name, p.activeProfile.name)
        assertTrue(p.userProfiles.single().sameLayers(mine))
        assertLive(mine, p)
    }

    fun testRestoringADefaultBackupMovesToTheNewDefault() {
        val backup = JSONObject()
            .put(Prefs.KEY_LAYOUT, legacy.toJson())
            .put(Prefs.KEY_SWIPE_GRID, grids(listOf(legacySwipe)))
            .put(Prefs.KEY_SYMBOL_PAGES, grids(legacySymbols))
            .put(Prefs.KEY_PROFILES, "[]")
            .put(Prefs.KEY_ACTIVE_PROFILE, Profile.DEFAULT.name)
        val p = prefs()
        p.importJson(backup.toString())
        assertEquals(Profile.DEFAULT.name, p.activeProfile.name)
        assertLive(Profile.DEFAULT, p)
    }

    fun testStoredLayoutWithoutGridsPinsDigitsToTheirOldCells() {
        // Bản rất cũ: chỉ có lớp chữ, lớp vuốt xuống / 123 đi theo chữ
        raw().edit().putString(Prefs.KEY_LAYOUT, legacy.toJson()).commit()
        assertLive(legacyProfile, prefs())
    }

    fun testNoStoredLayoutBuildsTheDefaultLayersWithLegacySwipeKeys() {
        // Bản rất cũ dùng bố cục mặc định, chỉ đổi ký tự vuốt xuống của Q (lưu theo phím)
        raw().edit().putString(Prefs.KEY_CUSTOM, JSONObject().put("q", "x").toString()).commit()
        val p = prefs()
        val (r, c) = LayoutModel.DEFAULT.find("q")!!
        assertEquals(KeyLayers.DEFAULT_SWIPE.with(r, c, "x"), p.swipeGrid)
        assertEquals(SymbolPages.DEFAULT, p.symbolPages)
    }
}
