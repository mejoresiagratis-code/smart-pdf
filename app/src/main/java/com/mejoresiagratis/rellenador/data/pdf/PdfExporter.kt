package com.mejoresiagratis.rellenador.data.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Genera el PDF final (relleno + firmado) y lo pone a disposición para
 * compartir o guardar mediante FileProvider.
 */
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val filler: AcroFormFiller
) {
    private fun contractTemplate(userUri: Uri?): InputStream =
        if (userUri != null) context.contentResolver.openInputStream(userUri)!!
        else context.assets.open("contrato-base.pdf")

    /** Genera y devuelve el File en almacenamiento interno de la app. */
    fun generateToFile(
        userContractUri: Uri?,
        values: Map<String, String>,
        signature: SignatureData?,
        stamps: List<SignatureStamp>,
        checkboxes: Map<String, String> = emptyMap(),
        fieldMapping: Map<String, String> = emptyMap(),
        fileName: String = "contrato-relleno.pdf"
    ): File {
        val outDir = File(context.filesDir, "output").apply { mkdirs() }
        val outFile = File(outDir, fileName)
        contractTemplate(userContractUri).use { tpl ->
            outFile.outputStream().use { os ->
                filler.generate(
                    template = tpl, values = values,
                    signature = signature, stamps = stamps, output = os,
                    checkboxes = checkboxes, fieldMapping = fieldMapping
                )
            }
        }
        return outFile
    }

    /** Genera un PDF temporal para previsualizar (mismo contenido que el final). */
    fun generatePreview(
        userContractUri: Uri?,
        values: Map<String, String>,
        signature: com.mejoresiagratis.rellenador.data.model.SignatureData?,
        stamps: List<SignatureStamp>,
        checkboxes: Map<String, String> = emptyMap(),
        fieldMapping: Map<String, String> = emptyMap()
    ): File = generateToFile(
        userContractUri, values, signature, stamps, checkboxes, fieldMapping,
        fileName = "preview.pdf"
    )

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareIntent(file: File): Intent {
        val uri = uriFor(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Intent para "guardar" vía SAF (crea documento donde el usuario elija). */
    fun createDocumentIntentName(): String = "contrato-relleno.pdf"
}
