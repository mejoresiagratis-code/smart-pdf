package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.model.ProxyProviders
import com.mejoresiagratis.rellenador.data.model.ProxyRequest
import com.mejoresiagratis.rellenador.data.model.ProxyResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ProxyApi {
    /** GET → qué motores tienen clave en el servidor. */
    @GET("ai-proxy.php")
    suspend fun providers(): ProxyProviders

    /** POST → extracción / localización de firma. */
    @POST("ai-proxy.php")
    suspend fun call(@Body request: ProxyRequest): ProxyResponse
}
