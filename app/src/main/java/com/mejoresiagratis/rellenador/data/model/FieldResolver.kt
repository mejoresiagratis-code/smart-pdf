package com.mejoresiagratis.rellenador.data.model

import com.mejoresiagratis.rellenador.ui.wizard.FieldCandidate
import com.mejoresiagratis.rellenador.ui.wizard.FieldOrigin
import com.mejoresiagratis.rellenador.ui.wizard.FieldState

/**
 * Funde lo que antes hacía la pantalla "Revisión IA" en datos por campo, para que el paso
 * de Relleno llegue prerrellenado y con los conflictos marcados en el propio campo
 * (v0.8.0).
 *
 * Entradas:
 *  - `proposals`: campos sueltos con sus candidatos y el consenso de motores.
 *  - `packages`: bloques coherentes (dirección fiscal, dirección de comercio, empresa,
 *    representante, banco). Aportan la PROCEDENCIA real (`Paquete.fuente` = documento) y
 *    permiten enlazar los campos que viajan juntos (CP/Población/Provincia con Dirección).
 *
 * Salida: valores autorrellenados + estado, origen y alternativas de cada campo.
 *
 * Los bloques de dirección llegan SIN sufijo (`Dirección`, `CP`…). Aquí se decide destino:
 * el bloque de tipo `direccion` va al bloque fiscal y `direccion_comercio` al `_2`. Cuando
 * hay más de un candidato para el mismo destino, no se elige: se marca CONFLICT.
 */
object FieldResolver {

    /** Claves de dirección que admiten sufijo `_2` (mismas que usa `PackageApplier`). */
    private val ADDRESS_KEYS = listOf("Dirección", "CP", "Población", "Provincia")

    data class Resolution(
        val autoValues: Map<String, String>,
        val states: Map<String, FieldState>,
        val origins: Map<String, FieldOrigin>,
        val candidates: Map<String, List<FieldCandidate>>,
    )

    fun resolve(
        proposals: List<FieldProposal>,
        packages: List<Paquete>,
        alreadyFilled: Map<String, String>,
    ): Resolution {
        val candidates = LinkedHashMap<String, MutableList<FieldCandidate>>()

        // ── 1. Bloques → candidatos, con destino resuelto y campos enlazados ──
        packages.forEach { pk ->
            val toBlock2 = pk.tipo == "direccion_comercio"
            val isAddress = pk.tipo == "direccion" || pk.tipo == "direccion_comercio"
            val origin = FieldOrigin(document = pk.fuente, note = pk.etiqueta)

            if (isAddress) {
                val street = pk.datos["Dirección"]?.takeIf { it.isNotBlank() } ?: return@forEach
                val linked = ADDRESS_KEYS.drop(1).mapNotNull { k ->
                    pk.datos[k]?.takeIf { it.isNotBlank() }
                        ?.let { (if (toBlock2) "${k}_2" else k) to it }
                }.toMap()
                val key = if (toBlock2) "Dirección_2" else "Dirección"
                candidates.getOrPut(key) { mutableListOf() }
                    .add(FieldCandidate(street, origin, linked))
            } else {
                pk.datos.forEach { (k, v) ->
                    if (v.isNotBlank()) {
                        candidates.getOrPut(k) { mutableListOf() }.add(FieldCandidate(v, origin))
                    }
                }
            }
        }

        // ── 2. Campos sueltos → candidatos (el consenso de motores va en el origen) ──
        proposals.forEach { p ->
            p.candidates.forEach { c ->
                if (c.value.isBlank()) return@forEach
                val list = candidates.getOrPut(p.fieldKey) { mutableListOf() }
                // Si un bloque ya aportó este mismo valor, se enriquece en vez de duplicar.
                val same = list.indexOfFirst { it.value.trim().equals(c.value.trim(), true) }
                val origin = FieldOrigin(
                    document = c.note.ifBlank { "documento analizado" },
                    engines = c.sources,
                    note = c.note,
                )
                if (same >= 0) {
                    val prev = list[same]
                    list[same] = prev.copy(
                        origin = prev.origin.copy(engines = prev.origin.engines + c.sources)
                    )
                } else {
                    list.add(FieldCandidate(c.value, origin))
                }
            }
        }

        // ── 3. Decidir estado por campo y componer el autorrelleno ──
        val autoValues = LinkedHashMap<String, String>()
        val states = LinkedHashMap<String, FieldState>()
        val origins = LinkedHashMap<String, FieldOrigin>()

        candidates.forEach { (key, list) ->
            // Un campo ya rellenado (fecha, responsable, o escrito por el usuario) no se pisa.
            if (alreadyFilled[key]?.isNotBlank() == true) {
                states[key] = FieldState.USER
                return@forEach
            }
            val distinct = list.distinctBy { it.value.trim().lowercase() }
            when (val state = AutoFillPolicy.decide(key, distinct)) {
                FieldState.AI -> {
                    val chosen = distinct.first()
                    autoValues[key] = chosen.value
                    chosen.linked.forEach { (lk, lv) ->
                        if (alreadyFilled[lk].isNullOrBlank()) {
                            autoValues[lk] = lv
                            states[lk] = FieldState.AI
                            origins[lk] = chosen.origin
                        }
                    }
                    states[key] = state
                    origins[key] = chosen.origin
                }
                // CONFLICT y WARN NO se autorrellenan: exigen decisión del usuario.
                else -> states[key] = state
            }
        }

        return Resolution(autoValues, states, origins, candidates.mapValues { it.value.toList() })
    }
}
