package com.apkstudio.app.util

import android.content.Context
import com.apkstudio.app.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * A user-facing failure with a precise title, an actionable hint
 * and optional technical details.
 */
data class AppFailure(
    val title: String,
    val hint: String,
    val detail: String? = null
)

class FailureException(val failure: AppFailure) : Exception(failure.title)

object ErrorMapper {

    fun fromThrowable(ctx: Context, t: Throwable): AppFailure {
        if (t is FailureException) return t.failure
        return when (t) {
            is UnknownHostException -> AppFailure(
                ctx.getString(R.string.err_network_title),
                ctx.getString(R.string.err_network_hint),
                ctx.getString(R.string.err_network_detail)
            )
            is SocketTimeoutException -> AppFailure(
                ctx.getString(R.string.err_timeout_title),
                ctx.getString(R.string.err_timeout_hint),
                t.message?.take(300)
            )
            is SSLException -> AppFailure(
                ctx.getString(R.string.err_ssl_title),
                ctx.getString(R.string.err_ssl_hint),
                t.message?.take(300)
            )
            is HttpException -> fromHttp(ctx, t.code(), extractApiMessage(t))
            is IOException -> AppFailure(
                ctx.getString(R.string.err_network_title),
                ctx.getString(R.string.err_network_hint),
                t.message?.take(300)
            )
            else -> AppFailure(
                ctx.getString(R.string.err_unknown_title),
                ctx.getString(R.string.err_unknown_hint),
                (t.message ?: t.javaClass.simpleName).take(400)
            )
        }
    }

    fun fromHttp(ctx: Context, code: Int, apiMessage: String?): AppFailure {
        val msg = apiMessage?.take(400)
        return when (code) {
            401 -> AppFailure(
                ctx.getString(R.string.err_401_title),
                ctx.getString(R.string.err_401_hint),
                msg ?: ctx.getString(R.string.err_401_detail)
            )
            403 -> when {
                msg != null && msg.contains("rate limit", ignoreCase = true) -> AppFailure(
                    ctx.getString(R.string.err_403_rate_title),
                    ctx.getString(R.string.err_403_rate_hint),
                    msg
                )
                msg != null && msg.contains("resource not accessible", ignoreCase = true) -> AppFailure(
                    ctx.getString(R.string.err_403_scope_title),
                    ctx.getString(R.string.err_403_scope_hint),
                    msg
                )
                else -> AppFailure(
                    ctx.getString(R.string.err_403_title),
                    ctx.getString(R.string.err_403_hint),
                    msg ?: "HTTP 403"
                )
            }
            404 -> AppFailure(
                ctx.getString(R.string.err_404_title),
                ctx.getString(R.string.err_404_hint),
                msg ?: "HTTP 404"
            )
            422 -> if (msg != null && msg.contains("actions", ignoreCase = true) &&
                (msg.contains("disabled", ignoreCase = true) || msg.contains("enabled", ignoreCase = true))
            ) {
                AppFailure(
                    ctx.getString(R.string.err_422_actions_title),
                    ctx.getString(R.string.err_422_actions_hint),
                    msg
                )
            } else {
                AppFailure(
                    ctx.getString(R.string.err_422_title),
                    ctx.getString(R.string.err_422_hint),
                    msg ?: "HTTP 422"
                )
            }
            409 -> AppFailure(
                ctx.getString(R.string.err_409_title),
                ctx.getString(R.string.err_409_hint),
                msg ?: "HTTP 409"
            )
            else -> AppFailure(
                "${ctx.getString(R.string.err_http_title)} ($code)",
                ctx.getString(R.string.err_http_hint),
                msg ?: "HTTP $code"
            )
        }
    }

    private fun extractApiMessage(e: HttpException): String? {
        return try {
            val body = e.response()?.errorBody()?.string() ?: return null
            Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
        } catch (_: Exception) {
            null
        }
    }
}
