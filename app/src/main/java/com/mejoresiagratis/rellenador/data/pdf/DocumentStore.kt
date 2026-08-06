package com.mejoresiagratis.rellenador.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copia los documentos aportados a almacenamiento privado de la app (Fase 2 de
 * robustez, v0.8.7).
 *
 * ── El problema ──
 * El selector devuelve un `content://` cuyo permiso de lectura es efímero: vive mientras
 * viva el proceso. Si Android mata la app en segundo plano (algo habitual mientras el
 * comercial hace fotos o consulta WhatsApp), al volver el URI restaurado ya no se puede
 * abrir y había que **volver a añadir todos los documentos**. Hasta ahora solo se
 * avisaba de ello; ahora se evita.
 *
 * `takePersistableUriPermission` no sirve como solución general: solo funciona si quien
 * abrió el selector concedió el permiso como persistible, y los documentos llegan por
 * rutas muy variadas (WhatsApp, cámara, Descargas…). Copiar los bytes es lo único que
 * no depende del proveedor del URI.
 *
 * Los ficheros viven en `filesDir/docs/` y se borran al empezar un contrato nuevo.
 */
@Singleton
class DocumentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir: File
        get() = File(context.filesDir, "docs").apply { if (!exists()) mkdirs() }

    /**
     * Copia [uris] a almacenamiento propio y devuelve los nuevos URIs (`file://`).
     * Si una copia falla se conserva el URI original: es mejor un documento con permiso
     * efímero que perderlo.
     */
    suspend fun persist(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            if (isOwned(uri)) return@map uri          // ya es nuestro: no duplicar
            runCatching { copyIn(uri) }.getOrDefault(uri)
        }
    }

    /** ¿Este URI ya apunta a nuestra copia privada? */
    fun isOwned(uri: Uri): Boolean =
        uri.scheme == "file" && uri.path?.startsWith(dir.absolutePath) == true

    private fun copyIn(uri: Uri): Uri {
        val name = displayName(uri)
        // Prefijo único: dos documentos pueden llamarse igual (típico con WhatsApp,
        // donde el mismo nombre se repite entre lotes distintos).
        val target = File(dir, "${System.currentTimeMillis()}_${name.take(80).sanitize()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo abrir $uri")
        return target.toUri()
    }

    /** Nombre visible del fichero; el `lastPathSegment` de un `content://` es un ID. */
    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "documento"

    private fun String.sanitize(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Borra las copias. Se llama al empezar un contrato nuevo. */
    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Elimina la copia de un documento concreto (al quitarlo de la lista). */
    fun delete(uri: Uri) {
        if (!isOwned(uri)) return
        runCatching { uri.path?.let { File(it).delete() } }
    }
}
