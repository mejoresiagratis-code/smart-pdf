package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Si el usuario ha configurado una URL de proxy propia en Ajustes, reescribe el
 * host/base de cada petición manteniendo el endpoint (ai-proxy.php) y la query.
 * Si no hay override (vacío), la petición sale con la URL de compilación tal cual.
 */
class DynamicBaseUrlInterceptor @Inject constructor(
    private val prefs: PrefsRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Lectura bloqueante aceptable: corre en el hilo de OkHttp, no en el principal,
        // y DataStore resuelve casi instantáneo.
        val override = runBlocking { runCatching { prefs.proxyBaseUrlOverride.first() }.getOrDefault("") }
        if (override.isBlank()) return chain.proceed(original)

        val customBase = override.toHttpUrlOrNull() ?: return chain.proceed(original)
        val endpointSegment = original.url.pathSegments.lastOrNull { it.isNotBlank() } ?: "ai-proxy.php"

        val newUrlBuilder = customBase.newBuilder().addPathSegments(endpointSegment)
        original.url.queryParameterNames.forEach { name ->
            original.url.queryParameterValues(name).forEach { value ->
                if (value != null) newUrlBuilder.addQueryParameter(name, value)
            }
        }
        val newRequest = original.newBuilder().url(newUrlBuilder.build()).build()
        return chain.proceed(newRequest)
    }
}
