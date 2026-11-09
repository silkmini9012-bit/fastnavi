package kr.fastnavi.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.fastnavi.app.ApiException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun get(url: String, headers: Map<String, String>): JSONObject =
        execute(Request.Builder().url(url).get(), headers)

    suspend fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject =
        execute(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)), headers)

    private suspend fun execute(builder: Request.Builder, headers: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            headers.forEach { (k, v) -> builder.header(k, v) }
            builder.header("Accept", "application/json")
            try {
                client.newCall(builder.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (!resp.isSuccessful) {
                        throw ApiException(errorMessage(resp.code, json, text))
                    }
                    json ?: throw ApiException("응답을 해석할 수 없습니다")
                }
            } catch (e: IOException) {
                throw ApiException("네트워크 오류: ${e.message ?: "연결 실패"}")
            }
        }

    /** 각 API마다 다른 에러 포맷에서 사람이 읽을 메시지를 최대한 뽑아냄 */
    private fun errorMessage(code: Int, json: JSONObject?, raw: String): String {
        val detail = json?.let { j ->
            j.optJSONObject("error")?.let { e ->
                e.optString("message").ifBlank { e.optString("msg") }
            } ?: j.optString("msg").ifBlank { j.optString("message") }
        }.orEmpty().ifBlank { raw.take(120) }
        val hint = when (code) {
            401, 403 -> "API 키 확인 필요"
            429 -> "호출 한도 초과 또는 API 미활성화"
            else -> "HTTP $code"
        }
        return if (detail.isBlank()) hint else "$hint ($detail)"
    }
}
