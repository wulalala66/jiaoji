package com.gluelab.jiaoji

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 发给模型的消息格式：带图的消息必须是 OpenAI 的多模态数组，
 * 不带图的老格式（纯字符串 content）不能变 —— 大部分中转对数组格式的兼容性更差。
 *
 * 必须跑在 Robolectric 上：裸 JVM 里的 org.json 是 Android 的空壳实现，
 * 所有方法都返回 null，测了等于没测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiMessageJsonTest {

    @Test
    fun plainMessageKeepsStringContent() {
        val json = ChatMsg(role = "user", content = "补到 100%").toJson()
        assertEquals("user", json.getString("role"))
        assertEquals("补到 100%", json.getString("content"))
    }

    @Test
    fun messageWithImagesUsesContentArray() {
        val json = ChatMsg(
            role = "user",
            content = "看看这张配料单",
            images = listOf("data:image/jpeg;base64,AAAA"),
        ).toJson()

        val content = json.get("content")
        assertTrue("带图时 content 应该是数组", content is JSONArray)
        val arr = content as JSONArray
        assertEquals(2, arr.length())
        assertEquals("text", arr.getJSONObject(0).getString("type"))
        assertEquals("看看这张配料单", arr.getJSONObject(0).getString("text"))

        val img = arr.getJSONObject(1)
        assertEquals("image_url", img.getString("type"))
        assertEquals("data:image/jpeg;base64,AAAA", img.getJSONObject("image_url").getString("url"))
    }

    @Test
    fun imageOnlyMessageOmitsEmptyTextPart() {
        val arr = ChatMsg(role = "user", content = "", images = listOf("data:image/png;base64,BBBB"))
            .toJson().getJSONArray("content")
        assertEquals(1, arr.length())
        assertEquals("image_url", arr.getJSONObject(0).getString("type"))
    }

    @Test
    fun assistantToolCallStillUsesToolCallsShape() {
        val json = ChatMsg(
            role = "assistant",
            content = "",
            toolCalls = listOf(AiCall("c1", "add_item", "{\"name\":\"A\"}")),
        ).toJson()
        assertTrue("content 应该是 null", json.isNull("content"))
        val calls = json.getJSONArray("tool_calls")
        assertEquals(1, calls.length())
        assertEquals("add_item", calls.getJSONObject(0).getJSONObject("function").getString("name"))
    }

    @Test
    fun toolResultCarriesToolCallId() {
        val json = ChatMsg(role = "tool", content = "{\"ok\":true}", toolCallId = "c1", name = "add_item").toJson()
        assertEquals("c1", json.getString("tool_call_id"))
        assertEquals("add_item", json.getString("name"))
        assertFalse(json.has("tool_calls"))
    }
}
