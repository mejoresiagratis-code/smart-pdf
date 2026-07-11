package com.mejoresiagratis.rellenador.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mejoresiagratis.rellenador.BuildConfig
import com.mejoresiagratis.rellenador.data.remote.ProxyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // CRÍTICO: sin esto, kotlinx.serialization OMITE del JSON cualquier campo que
        // tenga su valor por defecto (p.ej. geminiMode="g35", que casi siempre vale
        // exactamente eso). El proxy PHP entonces ve la clave "gemini_mode" inexistente
        // en vez de "g35", y en el caso de Gemini eso provoca un TypeError fatal
        // (parámetro no-nulo recibiendo null) — confirmado en el log real del servidor.
        encodeDefaults = true
    }

    @Provides @Singleton
    fun okHttp(dynamicBaseUrlInterceptor: com.mejoresiagratis.rellenador.data.remote.DynamicBaseUrlInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // AI calls can be slow
        .addInterceptor(dynamicBaseUrlInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.PROXY_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun proxyApi(retrofit: Retrofit): ProxyApi = retrofit.create(ProxyApi::class.java)
}
