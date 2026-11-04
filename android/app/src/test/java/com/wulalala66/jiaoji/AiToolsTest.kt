package com.wulalala66.jiaoji

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AI 工具的读写必须真的落到配方上（模型只会调工具，改得对不对全看这里）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiToolsTest {

    private fun ctx() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun seed() {
        Repo.I.init(ctx())
        Repo.I.clearAll()
        AppConfig.debounceMs = 0
        // 每个用例自己造一个配方：clearAll 不清配方，靠历史数据会互相污染
        state().formulas.map { it.id }.forEach { Repo.I.removeFormula(it) }
        val id = Repo.I.addFormula("测试底涂胶", 600.0)
        Repo.I.addFormulaItem(id, "D1NP", 26.33)
        Repo.I.addFormulaItem(id, "CC-400", 16.67)
    }

    private fun state() = Repo.I.state.value

    private fun firstFormulaId() = state().formulas.first().id

    @Test
    fun getFormulaReturnsItemsAndTotals() {
        val id = firstFormulaId()
        val json = JSONObject(AiTools.run(state(), "get_formula", "{\"formulaId\":\"$id\"}"))
        assertEquals(id, json.getString("id"))
        assertTrue(json.getJSONArray("items").length() > 0)
        assertTrue(json.has("sum"))
        assertTrue(json.has("rest"))
    }

    @Test
    fun addItemThenFillRestReachesHundred() {
        val id = firstFormulaId()
        AiTools.run(state(), "clear_items", "{\"formulaId\":\"$id\"}")
        AiTools.run(state(), "add_item", "{\"formulaId\":\"$id\",\"name\":\"D1NP\",\"percent\":30}")
        AiTools.run(state(), "add_item", "{\"formulaId\":\"$id\",\"name\":\"CC-400\",\"percent\":40}")
        val before = JSONObject(AiTools.run(state(), "get_formula", "{\"formulaId\":\"$id\"}"))
        assertEquals(70.0, before.getDouble("sum"), 0.01)

        val after = JSONObject(AiTools.run(state(), "fill_rest", "{\"formulaId\":\"$id\",\"item\":\"D1NP\"}"))
        assertEquals(100.0, after.getDouble("sum"), 0.01)
        assertEquals(0.0, after.getDouble("rest"), 0.01)
        // 补给的是 D1NP：30 + 30 = 60
        val items = after.getJSONArray("items")
        var d1 = 0.0
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            if (o.getString("name") == "D1NP") d1 = o.getDouble("percent")
        }
        assertEquals(60.0, d1, 0.01)
    }

    @Test
    fun addItemTwiceWithSameNameUpdatesInsteadOfDuplicating() {
        val id = firstFormulaId()
        AiTools.run(state(), "clear_items", "{\"formulaId\":\"$id\"}")
        AiTools.run(state(), "add_item", "{\"formulaId\":\"$id\",\"name\":\"色浆\",\"percent\":1.5}")
        AiTools.run(state(), "add_item", "{\"formulaId\":\"$id\",\"name\":\"色浆\",\"percent\":2.5}")
        val f = state().formulaOf(id)!!
        assertEquals(1, f.items.size)
        assertEquals(2.5, f.items.first().percent, 0.001)
    }

    @Test
    fun updateAndRemoveItemByName() {
        val id = firstFormulaId()
        AiTools.run(state(), "clear_items", "{\"formulaId\":\"$id\"}")
        AiTools.run(state(), "add_item", "{\"formulaId\":\"$id\",\"name\":\"D-80\",\"percent\":6.5}")
        AiTools.run(state(), "update_item", "{\"formulaId\":\"$id\",\"item\":\"d-80\",\"percent\":8}")
        assertEquals(8.0, state().formulaOf(id)!!.items.first().percent, 0.001)
        AiTools.run(state(), "remove_item", "{\"formulaId\":\"$id\",\"item\":\"D-80\"}")
        assertEquals(0, state().formulaOf(id)!!.items.size)
    }

    @Test
    fun setTotalRescalesGramsNotPercent() {
        val id = firstFormulaId()
        AiTools.run(state(), "clear_items", "{\"formulaId\":\"$id\"}")
        AiTools.run(state(), "add_item", "{\"formulaId\":\"$id\",\"name\":\"D1NP\",\"percent\":50}")
        AiTools.run(state(), "set_formula_total", "{\"formulaId\":\"$id\",\"totalG\":1000}")
        val f = state().formulaOf(id)!!
        assertEquals(50.0, f.items.first().percent, 0.001)
        assertEquals(500.0, f.gramsOf(f.items.first()), 0.001)
    }

    @Test
    fun createFormulaAndMeta() {
        val out = JSONObject(AiTools.run(state(), "create_formula", "{\"name\":\"低成本底涂\",\"totalG\":600}"))
        val id = out.getString("formulaId")
        assertTrue(state().formulaOf(id) != null)
        AiTools.run(state(), "set_formula_meta", "{\"formulaId\":\"$id\",\"purpose\":\"降粘\",\"cure\":\"140*30\"}")
        val f = state().formulaOf(id)!!
        assertEquals("降粘", f.purpose)
        assertEquals("140*30", f.cure)
    }

    @Test
    fun productsQueryFindsCustomer() {
        val json = JSONObject(AiTools.run(state(), "get_products", "{\"query\":\"安徽万安\"}"))
        val arr = json.getJSONArray("products")
        assertTrue(arr.length() >= 1)
        assertTrue(arr.getJSONObject(0).getString("name").contains("安徽万安"))
    }

    @Test
    fun calcToolComputes() {
        val out = AiTools.run(state(), "calc", "{\"expression\":\"(600*26.33)/100\"}")
        assertEquals("157.98", out)
    }

    @Test
    fun unknownToolReturnsError() {
        val out = AiTools.run(state(), "no_such_tool", "{}")
        assertTrue(out.contains("error"))
    }

    @Test
    fun missingFormulaGivesFriendlyError() {
        val out = AiTools.run(state(), "get_formula", "{\"formulaId\":\"nope\"}")
        assertTrue(out.contains("没有这个配方"))
    }
}
