package com.transcriptai.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.transcriptai.app.BuildConfig
import java.util.UUID

/**
 * Browser-based OAuth 2.0 authorization-code launcher for Microsoft + Yahoo. We only obtain the
 * authorization `code` here; the confidential token exchange (which needs the client secret) happens
 * server-side in /auth/login. The provider redirects back to [redirectUri] (a custom scheme caught by
 * MainActivity), carrying `code` + `state`.
 */
object OAuthClient {
    // Azure accepts the custom-scheme redirect; Yahoo requires an HTTPS one, so Yahoo points at a
    // backend callback (/api/auth/yahoo/callback) that 302s straight back to this custom scheme.
    private val yahooHttpsRedirect: String = BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/') + "/auth/yahoo/callback"
    private var pendingState: String? = null

    fun redirectUri(provider: String): String = when (provider.uppercase()) {
        "YAHOO" -> yahooHttpsRedirect
        else -> BuildConfig.OAUTH_REDIRECT_URI
    }

    fun clientId(provider: String): String = when (provider.uppercase()) {
        "MICROSOFT" -> BuildConfig.MICROSOFT_CLIENT_ID
        "YAHOO" -> BuildConfig.YAHOO_CLIENT_ID
        else -> ""
    }

    fun isConfigured(provider: String): Boolean = clientId(provider).isNotBlank()

    /** Open the provider's hosted sign-in page in the browser. Returns false if unconfigured. */
    fun launch(context: Context, provider: String): Boolean {
        val cid = clientId(provider)
        if (cid.isBlank()) return false
        val nonce = UUID.randomUUID().toString().take(10)
        val state = "${provider.uppercase()}:$nonce"
        pendingState = state
        val url = buildAuthorizeUrl(provider, cid, state) ?: return false
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { return false }
        return true
    }

    /** Validate the returned state and recover the provider it belongs to (null if mismatched). */
    fun providerForState(state: String?): String? {
        if (state.isNullOrBlank() || state != pendingState) return null
        pendingState = null
        return state.substringBefore(":")
    }

    private fun buildAuthorizeUrl(provider: String, clientId: String, state: String): String? {
        val redirect = Uri.encode(redirectUri(provider))
        return when (provider.uppercase()) {
            "MICROSOFT" -> "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                "?client_id=$clientId&response_type=code&redirect_uri=$redirect&response_mode=query" +
                "&scope=${Uri.encode("openid email profile User.Read")}&state=$state"
            "YAHOO" -> "https://api.login.yahoo.com/oauth2/request_auth" +
                "?client_id=$clientId&response_type=code&redirect_uri=$redirect" +
                "&scope=${Uri.encode("openid email profile")}&state=$state"
            else -> null
        }
    }
}
