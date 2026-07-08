package com.mejoresiagratis.rellenador.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AjustesScreen(onBack: () -> Unit, vm: AjustesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SectionTitle("Perfil comercial")
            Text(
                "Nombre que se autorrellena en el campo \"Responsable Comercial MASORANGE\".",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = ui.responsable,
                onValueChange = vm::setResponsable,
                label = { Text("Responsable Comercial") },
                placeholder = { Text(ui.responsablePlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            SectionTitle("Motores IA activos")
            Text(
                "Por defecto se usan todos los que tengan clave en el servidor. Aquí eliges cuáles " +
                    "prefieres consultar cuando analices documentación.",
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.allProviders.forEach { p ->
                    FilterChip(
                        selected = p in ui.enabledProviders,
                        onClick = { vm.toggleProvider(p) },
                        label = { Text(p.displayName + if (p.eu) " 🇪🇺" else "") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::save, modifier = Modifier.weight(1f)) { Text("Guardar cambios") }
                if (ui.justSaved) {
                    Text("Guardado ✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}
