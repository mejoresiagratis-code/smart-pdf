package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.model.ProxyRequest
import com.mejoresiagratis.rellenador.data.model.ProxyResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ProxyApi {
    /** Matches the endpoint exposed by ai-proxy.php. */
    @POST("ai-proxy.php")
    suspend fun extract(@Body request: ProxyRequest): ProxyResponse
}
