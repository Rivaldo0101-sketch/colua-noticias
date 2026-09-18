package com.example.coluainformativa.repository

import android.content.Context
import android.content.SharedPreferences
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import com.example.coluainformativa.LoginActivity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.coluainformativa.database.*
import com.example.coluainformativa.security.AdminAuthManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.installations.FirebaseInstallations
import java.util.*
import java.util.concurrent.TimeUnit

class ColuaRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance().apply {
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestoreSettings = settings
    }
    private val authManager = AdminAuthManager(context)
    private val localDb = AppDatabase.getDatabase(context)

    init {
        try {
            localDb.sectionDao().deleteById("sec_comunidad")
            localDb.sectionDao().deleteById("sec_prueba")
            localDb.navigationDao().deleteByTargetSection("sec_comunidad")
            localDb.navigationDao().deleteByTargetSection("sec_prueba")
            localDb.navigationDao().deleteById("side_comunidad")
            localDb.navigationDao().deleteById("nav_sec_prueba")

            // Asegurar que Sostenibilidad Cooperativa exista siempre localmente
            if (localDb.sectionDao().getAllSections().none { it.id.equals("sec_sostenibilidad", ignoreCase = true) }) {
                localDb.sectionDao().insert(SectionEntity("sec_sostenibilidad", "Sostenibilidad Cooperativa", "sostenibilidad", "Cursos y centros de innovación de la cooperativa", "sostenibilidad_cooperativa", "#59B8A4", 11, true))
            }
            if (localDb.navigationDao().getAllItems().none { it.id.equals("side_sostenibilidad", ignoreCase = true) }) {
                localDb.navigationDao().insert(NavigationItemEntity("side_sostenibilidad", "Sostenibilidad Cooperativa", "sostenibilidad_cooperativa", "sec_sostenibilidad", "SIDEBAR", 6))
            }

            val allSections = localDb.sectionDao().getAllSections()
            for (s in allSections) {
                if (s.id.contains("prueba", ignoreCase = true) || s.title.contains("prueba", ignoreCase = true)) {
                    localDb.sectionDao().deleteById(s.id)
                    localDb.navigationDao().deleteByTargetSection(s.id)
                }
            }

            if (isCloudEnabled()) {
                firestore.collection("sections").document("sec_prueba").delete()
                firestore.collection("navigation_items").document("nav_sec_prueba").delete()
                firestore.collection("sections").whereEqualTo("id", "sec_prueba").get().addOnSuccessListener { docs ->
                    for (doc in docs.documents) doc.reference.delete()
                }
                firestore.collection("navigation_items").whereEqualTo("targetSectionId", "sec_prueba").get().addOnSuccessListener { docs ->
                    for (doc in docs.documents) doc.reference.delete()
                }
            }
        } catch (e: Exception) {}
    }

    fun getInstallationId(callback: (String) -> Unit) {
        val pref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        var installId = pref.getString("installation_uuid", null)
        if (installId.isNullOrEmpty()) {
            installId = UUID.randomUUID().toString()
            pref.edit().putString("installation_uuid", installId).apply()
        }
        callback(installId)
    }

    fun getGuestId(callback: (String) -> Unit) {
        val pref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        var guestId = pref.getString("guest_user_id", null)
        if (guestId.isNullOrEmpty()) {
            guestId = "guest_${UUID.randomUUID().toString().substring(0, 8)}"
            pref.edit().putString("guest_user_id", guestId).apply()
        }
        callback(guestId)
    }

    private fun <T> await(task: com.google.android.gms.tasks.Task<T>): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        return try { Tasks.await(task, 2, TimeUnit.SECONDS) } catch (e: Exception) { null }
    }

    private var activeConfigListener: ListenerRegistration? = null

    fun unsubscribeAll() {
        try {
            activeConfigListener?.remove()
            activeConfigListener = null
        } catch (e: Exception) {
            Log.e("REPO", "Error al desuscribir listeners: ${e.message}")
        }
    }

    private fun isCloudEnabled(): Boolean = authManager.isCloudSyncEnabled

    // --- SECCIONES (ROBUSTO Y LOCAL-FIRST PARA EVITAR DESTELLOS) ---
    fun getAllSections(): List<SectionEntity> {
        cleanupOrphanNavigationItems()
        var local = localDb.sectionDao().getAllSections()
        if (local.isEmpty()) {
            DataSeeder.seedIfEmpty(context)
            local = localDb.sectionDao().getAllSections()
        }
        return local
    }

    fun getVisibleSections(): List<SectionEntity> {
        var local = localDb.sectionDao().getPublishedSections()
        if (local.isEmpty()) {
            DataSeeder.seedIfEmpty(context)
            local = localDb.sectionDao().getPublishedSections()
        }
        return local
    }

    fun getArchivedSections(): List<SectionEntity> {
        return localDb.sectionDao().getDeletedSections()
    }

    fun insertSection(section: SectionEntity) {
        if (section.id.isEmpty()) section.id = UUID.randomUUID().toString()
        localDb.sectionDao().insert(section)
        if (isCloudEnabled()) firestore.collection("sections").document(section.id).set(section)
    }

    fun archiveSection(id: String) {
        if ("sec_home".equals(id, ignoreCase = true)) {
            Log.e("REPO", "No se permite archivar la pantalla de Inicio (sec_home).")
            return
        }
        val section = localDb.sectionDao().getSectionById(id) ?: return
        section.deletedAt = System.currentTimeMillis()
        section.isVisible = false
        section.isPublished = false
        section.updatedAt = System.currentTimeMillis()
        localDb.sectionDao().insert(section)
        localDb.navigationDao().deleteByTargetSection(id)

        context.getSharedPreferences("ConfigSyncPrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("has_unpublished_changes", true).apply()

        if (isCloudEnabled()) {
            firestore.collection("sections").document(id).set(section)
            firestore.collection("navigation_items").whereEqualTo("targetSectionId", id).get().addOnSuccessListener { docs ->
                docs.forEach { it.reference.delete() }
            }
        }
    }

    fun restoreArchivedSection(id: String) {
        val section = localDb.sectionDao().getDeletedSections().firstOrNull { it.id == id } ?: return
        section.deletedAt = null
        section.isVisible = true
        section.updatedAt = System.currentTimeMillis()
        localDb.sectionDao().insert(section)

        context.getSharedPreferences("ConfigSyncPrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("has_unpublished_changes", true).apply()

        if (isCloudEnabled()) {
            firestore.collection("sections").document(id).set(section)
        }
    }

    fun deleteSection(id: String) {
        archiveSection(id)
    }

    fun purgeSectionPermanently(id: String) {
        if ("sec_home".equals(id, ignoreCase = true)) return
        localDb.sectionDao().deleteById(id)
        localDb.navigationDao().deleteByTargetSection(id)
        localDb.navigationDao().deleteById("nav_$id")
        localDb.contentDao().deleteItemsBySection(id)
        localDb.contentDao().deleteBlocksBySection(id)

        context.getSharedPreferences("ConfigSyncPrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("has_unpublished_changes", true).apply()

        if (isCloudEnabled()) {
            firestore.collection("sections").document(id).delete()
            firestore.collection("navigation_items").document("nav_$id").delete()
        }
    }

    fun cleanupOrphanNavigationItems() {
        try {
            val sections = localDb.sectionDao().getAllSections()
            val validSectionIds = sections.map { it.id }.toSet()
            val allNavItems = localDb.navigationDao().getAllItems()
            for (nav in allNavItems) {
                val target = nav.targetSectionId
                if (target.startsWith("sec_") && !validSectionIds.contains(target) && 
                    target != "sec_home" && target != "sec_agencias" && target != "sec_servicios" && 
                    target != "sec_beneficios" && target != "sec_noticias" && target != "sec_nosotros" && 
                    target != "sec_creditos" && target != "sec_seguros" && target != "sec_remesas" && 
                    target != "sec_ahorros") {
                    
                    localDb.navigationDao().deleteByTargetSection(target)
                    localDb.navigationDao().deleteById(nav.id)
                    if (isCloudEnabled()) {
                        firestore.collection("navigation_items").document(nav.id).delete()
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    // --- CONTENIDO ---
    fun resolveSectionId(sectionIdOrSlug: String): String {
        val sections = getAllSections()
        val match = sections.find { it.id.equals(sectionIdOrSlug, ignoreCase = true) || it.slug.equals(sectionIdOrSlug, ignoreCase = true) }
        return match?.id ?: sectionIdOrSlug
    }

    private fun ensureSostenibilidadItemsSynced() {
        try {
            val seedItems = listOf(
                ContentItemEntity(
                    "item_sostenibilidad_1",
                    "sec_sostenibilidad",
                    "Educación y Formación Cooperativa",
                    "Ver más detalles",
                    "Empoderando a la niñez, juventud y comunidades rurales del departamento con herramientas financieras, valores y liderazgo.\n\n• Programa Wachalal (2,284 Alumnos): Formación lúdica y fomento al ahorro en Concepción, Panajachel, Santiago Atitlán, Sololá y San Juan La Laguna.\n\n• Educación Financiera (1,305 Personas): Talleres prácticos en Nahualá, Santiago Atitlán, San Andrés Semetabaj y Concordia Totonicapán.\n\n• Becas Jóvenes Cooperativistas (160 Becados): Impulso académico y mentoría solidaria para nuevas generaciones de líderes locales.\n\n• Programa Huellas (436 Atendidos): Fortalecimiento de valores éticos para estudiantes y colaboradores del equipo COLUA.",
                    "#173789",
                    1
                ).apply {
                    imagePath = "sostenibilidad_cooperativa"
                    tags = "#COLUAEducación #Wachalal #MICOOPE"
                    isFeatured = true
                    isDraft = false
                    targetSectionId = "https://coluarl.com.gt/"
                },
                ContentItemEntity(
                    "item_sostenibilidad_2",
                    "sec_sostenibilidad",
                    "Nuestra Historia y Raíces",
                    "Conoce más",
                    "Desde el 22 de mayo de 1965\n\nCOLUA MICOOPE nació gracias al coraje de 25 visionarios guiados por la misionera Elena Harding. Con un aporte inicial de Q5.00 y cuotas semanales de Q0.25, demostraron que la solidaridad comunitaria es el motor financiero más poderoso.\n\n• 60 Años de solidez\n• 25 Socios pioneros\n• Q5.00 Capital semilla",
                    "#59B8A4",
                    2
                ).apply {
                    imagePath = "grupo"
                    tags = "#HistoriaCOLUA #60Años #MICOOPE"
                    isFeatured = true
                    isDraft = false
                    targetSectionId = "https://coluarl.com.gt/"
                },
                ContentItemEntity(
                    "item_sostenibilidad_3",
                    "sec_sostenibilidad",
                    "Nuestra Propuesta de Valor",
                    "Leer más",
                    "“En COLUA reconocemos tu valor como persona para alcanzar tu bienestar integral y el de tu familia.”",
                    "#E42A67",
                    3
                ).apply {
                    imagePath = "noticias_colua"
                    tags = "#PropuestaDeValor #BienestarIntegral #MICOOPE"
                    isFeatured = false
                    isDraft = false
                    targetSectionId = "https://coluarl.com.gt/"
                },
                ContentItemEntity(
                    "item_sostenibilidad_4",
                    "sec_sostenibilidad",
                    "Huella e Impacto Social (Sololá & Occidente)",
                    "Visitar sitio web oficial",
                    "Bienestar comunitario durante 2025\n\n• 22,775 Personas beneficiadas (Directas)\n• 18,672 Asociados y comunidad (Indirectas)\n• 41,447 Vidas tocadas (Impacto Global Acumulado)",
                    "#173789",
                    4
                ).apply {
                    imagePath = "sostenibilidad_cooperativa"
                    tags = "#ImpactoSocial #VidasTocadas #MICOOPE"
                    isFeatured = true
                    isDraft = false
                    targetSectionId = "https://coluarl.com.gt/"
                }
            )

            for (item in seedItems) {
                val existing = localDb.contentDao().getItemById(item.id)
                if (existing == null) {
                    localDb.contentDao().insertItem(item)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_items").document(item.id).set(item)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed sostenibilidad item to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureSostenibilidadItemsSynced: ${e.message}")
        }
    }

    fun getItemsBySection(sectionId: String): List<ContentItemEntity> {
        val cleanId = sectionId.lowercase(Locale.getDefault())
        if (cleanId == "sec_sostenibilidad" || cleanId == "sostenibilidad") {
            ensureSostenibilidadItemsSynced()
        }
        val altId = if (cleanId.startsWith("sec_")) cleanId.replace("sec_", "") else "sec_$cleanId"

        val local = localDb.contentDao().getItemsBySection(cleanId).toMutableList()
        val altLocal = localDb.contentDao().getItemsBySection(altId)

        for (item in altLocal) {
            if (local.none { it.id == item.id }) {
                local.add(item)
            }
        }

        if (isCloudEnabled()) {
            try {
                val task = firestore.collection("content_items").whereIn("sectionId", listOf(cleanId, altId)).get()
                val cloud = await(task)?.toObjects(ContentItemEntity::class.java)
                if (cloud != null && cloud.isNotEmpty()) {
                    cloud.forEach { cloudItem ->
                        localDb.contentDao().insertItem(cloudItem)
                        if (local.none { it.id == cloudItem.id }) {
                            local.add(cloudItem)
                        } else {
                            val idx = local.indexOfFirst { it.id == cloudItem.id }
                            if (idx >= 0) local[idx] = cloudItem
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("REPO", "Error syncing items from Firestore: ${e.message}")
            }
        }

        if (cleanId == "sec_noticias" || cleanId == "noticias") {
            return local.sortedByDescending { it.updatedAt }
        }

        return local.sortedBy { it.displayOrder }
    }

    fun getPublishedItemsBySection(sectionId: String): List<ContentItemEntity> {
        return getItemsBySection(sectionId).filter { !it.isDraft && it.isVisible }
    }

    fun getItemById(itemId: String): ContentItemEntity? {
        val local = localDb.contentDao().getItemById(itemId)
        if (!isCloudEnabled()) return local
        val task = firestore.collection("content_items").document(itemId).get()
        return await(task)?.toObject(ContentItemEntity::class.java) ?: local
    }

    fun insertItem(item: ContentItemEntity) {
        if (item.id.isEmpty()) item.id = UUID.randomUUID().toString()
        item.updatedAt = System.currentTimeMillis()
        localDb.contentDao().insertItem(item)
        if (isCloudEnabled()) firestore.collection("content_items").document(item.id).set(item)
    }

    fun deleteItemById(itemId: String) {
        localDb.contentDao().deleteItemById(itemId)
        if (isCloudEnabled()) firestore.collection("content_items").document(itemId).delete()
    }

    fun getAllItems(): List<ContentItemEntity> {
        return localDb.contentDao().getAllItems()
    }

    fun getAllBlocks(): List<ContentBlockEntity> {
        return localDb.contentDao().getAllBlocks()
    }

    // --- BLOQUES ---
    private fun ensureNosotrosBlocksSynced() {
        try {
            val seedBlocks = listOf(
                ContentBlockEntity("block_nosotros_propuesta", null, "CARD", "\"En COLUA reconocemos tu valor como persona para alcanzar tu bienestar integral y el de tu familia, a través de productos y servicios financieros éticos, ágiles y accesibles, basados en el poder de la cooperación\".", 1, "ic_verified", "Propuesta de Valor", null, null, "sec_nosotros", "#59B8A4", null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_nosotros_vision", null, "CARD", "\"Ser un modelo de desarrollo y sostenibilidad integral de las comunidades basado en la cooperación\".", 2, "ic_info", "Visión", null, null, "sec_nosotros", "#173789", null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_nosotros_proposito", null, "CARD", "\"Ser la cooperativa financiera que mejora la calidad de vida de sus asociados y comunidades de Guatemala\".", 3, "ic_campaign", "Propósito Visionario", null, null, "sec_nosotros", "#E42A67", null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_nosotros_valores_title", null, "TEXT", "", 4, null, "Valores de COLUA MICOOPE", null, null, "sec_nosotros", null, null, "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_nosotros_integridad", null, "TEXT", "Actuar con coherencia con nuestros valores, manteniendo transparencia en todo lo que hacemos y fomentando la cooperación en cada acción.", 5, null, "INTEGRIDAD", null, null, "sec_nosotros", null, null, "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_nosotros_cooperacion", null, "TEXT", "COOPERACIÓN:\nTrabajar juntos para alcanzar un objetivo común, basada en la ayuda mutua, la solidaridad y el esfuerzo compartido.\n\nRESPONSABILIDAD:\nAdministramos y cuidamos los ahorros de nuestros asociados que nos han confiado.", 6, null, "COOPERACIÓN Y RESPONSABILIDAD", null, null, "sec_nosotros", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_nosotros_grafico", null, "IMAGE", "", 7, "valores_colua_1", "Gráfico de Valores", null, null, "sec_nosotros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_nosotros_enfoque", null, "TEXT", "El centro de atención de nuestros esfuerzos y nuestra lealtad son los asociados, a quienes entregamos siempre soluciones de calidad.", 8, null, "ENFOQUE AL ASOCIADO", null, null, "sec_nosotros", null, null, "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true }
            )

            for (b in seedBlocks) {
                val existing = localDb.contentDao().getBlockById(b.id)
                if (existing == null) {
                    localDb.contentDao().insertBlock(b)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_blocks").document(b.id).set(b)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed block to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureNosotrosBlocksSynced: ${e.message}")
        }
    }

    private fun ensureAhorrosBlocksSynced() {
        try {
            val seedBlocks = listOf(
                ContentBlockEntity("block_ahorros_header", null, "CONTAINER", "Construye un futuro financiero sólido con nuestras cuentas de ahorro diseñadas para cada etapa de tu vida. Excelentes tasas, cero comisiones de manejo y total disponibilidad.", 1, "logo_composite", "NUESTRAS CUENTAS DE AHORRO", "Abre tu cuenta", "tel:77957795", "sec_ahorros", "#173789", "#173789", "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_ahorro_1", null, "CARD", "Es la cuenta que otorga el derecho a la persona natural a asociarse a la cooperativa, lo convierte en dueño con voz y voto en las decisiones de la asamblea general (no es una cuenta corriente).\n\n• Monto de apertura: desde Q50.00.\n• Tasa de interés: 5% anual afecto a ISR.\n• Intereses: capitalizables anualmente.", 2, null, "Cuenta Aportación Adulto", "Conocer Más", "", "sec_ahorros", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_ahorro_2", null, "CARD", "Es la cuenta que otorga el derecho al menor de edad a asociarse a la cooperativa e iniciar el hábito del ahorro (no es una cuenta corriente).\n\n• Monto de apertura: desde Q50.00.\n• Tasa de interés: 5% anual afecto a ISR.\n• Intereses: capitalizables anualmente.", 3, null, "Cuenta Aportación Infanto Juvenil", "Conocer Más", "", "sec_ahorros", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_ahorro_3", null, "CARD", "Es la cuenta diseñada para motivar y fomentar en los niños y adolescentes la cultura del ahorro.\n\n• Monto de apertura: desde Q10.00.\n• Tasa de interés: 3% anual afecto a ISR.\n• Obtiene 5 beneficios al mantener mínimo Q500.00 en su cuenta corriente después de 180 días de haberla aperturado.", 4, "ahorro_infanto_juvenil", "Cuenta Ahorro Infanto Juvenil", "Conocer Más", "", "sec_ahorros", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_ahorro_4", null, "CARD", "Es la cuenta que el asociado podrá utilizar para poder darle movimiento a sus ahorros de acuerdo con sus necesidades y conveniencias.\n\n• Monto de apertura: desde Q50.00 y/o $100.\n• Tasa de interés en Q: 3% anual afecto a ISR.\n• Tasa de interés en $: 1.50% anual afecto a ISR.\n• Intereses: capitalizables mensualmente.\n• Acceso a canales digitales sin costos.", 5, "ahorro_disponible", "Cuenta Ahorro Disponible", "Conocer Más", "", "sec_ahorros", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_ahorro_5", null, "CARD", "Es la cuenta que les permite a los asociados aportar cuotas fijas mensuales para un objetivo específico en el futuro.\n\n• Apertura desde Q25.00.\n• Tasa de interés: 7.50% anual, afecto a ISR.\n• Plazos de 3, 5, 10, 15 o 20 años.\n• Intereses capitalizables mensualmente.", 6, "ahorro_programado", "Cuenta Ahorro Programado", "Conocer Más", "", "sec_ahorros", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_ahorro_6", null, "CARD", "Es la cuenta que le permite al asociado obtener alto rendimiento y seguridad sobre sus ahorros.\n\n• Apertura desde Q1,000.00 y/o $200.\n• Plazos de 90, 180 y 365 días.\n• Intereses capitalizables trimestralmente.", 7, "ahorro_plazo_fijo", "Cuenta Ahorro Plazo Fijo", "Conocer Más", "", "sec_ahorros", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true }
            )

            for (b in seedBlocks) {
                val existing = localDb.contentDao().getBlockById(b.id)
                if (existing == null) {
                    localDb.contentDao().insertBlock(b)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_blocks").document(b.id).set(b)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed ahorro block to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureAhorrosBlocksSynced: ${e.message}")
        }
    }

    private fun ensureCreditosBlocksSynced() {
        try {
            val seedBlocks = listOf(
                ContentBlockEntity("block_creditos_header", null, "CONTAINER", "Soluciones financieras a tu medida. Impulsa tus proyectos con nosotros.", 1, "logo_composite", "NUESTROS CRÉDITOS", "PBX: 7795-7795", "tel:77957795", "sec_creditos", "#173789", "#173789", "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_1", null, "CARD", "• Ideal para capital de trabajo, inventarios y/o mercadería.\n• Adquisición y/o remodelación de activos fijos (mobiliario, herramientas, equipo).\n• Compra de vehículo.\n\nMonto: desde Q1,000.00 en adelante.", 2, "credito_productivo", "Crédito Productivo", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_2", null, "CARD", "• Gastos personales (varios destinos).\n• Adquisición de menaje de casa.\n• Compra de vehículo.\n• Crédito educativo.\n\nMonto: desde Q1,000.00 en adelante.", 3, "credito_consumo", "Crédito Consumo", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_3", null, "CARD", "• Mejoramiento y ampliación de vivienda residencial.\n• Construcción de vivienda nueva en terreno propio.\n• Compra de terreno con fines de vivienda.\n• Compra de vivienda.\n• Liberación de gravamen hipotecario (compra de deuda).\n\nMonto: desde Q1,000.00 en adelante.", 4, "credito_vivienda", "Crédito Vivienda", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_4", null, "CARD", "• Adquisición de motocicletas o vehículos para actividades comerciales o para uso personal.\n\nMonto: desde Q1,000.00 en adelante.", 5, "credi_vehiculo", "Crédi Vehículo", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_5", null, "CARD", "• Servicio, industria, capital de trabajo o inversiones productivas.\n\nMonto: desde Q1,000.00 en adelante.", 6, "credito", "Crédito MIPYMES", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_6", null, "CARD", "• Capital de trabajo destinado para siembra, renovación, mantenimiento e insumos para todo tipo de cultivo, siempre que este sea lícito.\n• Adquisición de activos fijos destinados para actividades agrícolas.\n\nMonto: desde Q1,000.00 en adelante.", 7, "credito", "Crédito Agrícola", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_7", null, "CARD", "• Libre disponibilidad (Cualquier destino).\n\nMonto: desde Q1,000.00 siempre y когда el monto del crédito no sea mayor al 90% del monto de la inversión.", 8, "credito", "Crédito Automático", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_credito_8", null, "CARD", "• Financiamiento para pequeños negocios dedicados a la producción, comercio o servicios, con pagos respaldados por los ingresos de sus ventas.\n\nMonto: desde Q1,000.00 en adelante.", 9, "credito", "Microcréditos", "Solicitar Ahora (PBX)", "tel:77957795", "sec_creditos", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true }
            )

            for (b in seedBlocks) {
                val existing = localDb.contentDao().getBlockById(b.id)
                if (existing == null) {
                    localDb.contentDao().insertBlock(b)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_blocks").document(b.id).set(b)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed credito block to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureCreditosBlocksSynced: ${e.message}")
        }
    }

    private fun ensureSegurosBlocksSynced() {
        try {
            val seedBlocks = listOf(
                ContentBlockEntity("block_seguros_header", null, "CONTAINER", "Tranquilidad para ti y tu familia con nuestras opciones de seguros adaptadas a tus necesidades.", 1, "seguros_columna", "Protegemos lo que más quieres", "PBX: 0000-0000", "tel:00000000", "sec_seguros", "#173789", "#173789", "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_seguro_1", null, "CARD", "", 2, "seguro_cv_personal", "Seguro CV Especial", "Solicitar Información →", "tel:00000000", "sec_seguros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_seguro_2", null, "CARD", "", 3, "seguro_vida_saludable", "Seguro Vida Saludable", "Solicitar Información →", "tel:00000000", "sec_seguros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_seguro_3", null, "CARD", "", 4, "seguro_edad_de_oro", "Seguro de Accidentes Edad de Oro", "Solicitar Información →", "tel:00000000", "sec_seguros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_seguro_4", null, "CARD", "", 5, "seguro_de_cancer", "Seguro de Cáncer", "Solicitar Información →", "tel:00000000", "sec_seguros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_seguro_5", null, "CARD", "", 6, "seguro_accidentes_infanto_juvenil", "Seguro de Accidentes Personales Infanto Juvenil", "Solicitar Información →", "tel:00000000", "sec_seguros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_seguro_6", null, "CARD", "", 7, "seguro_manejo", "Seguro de Manejo", "Solicitar Información →", "tel:00000000", "sec_seguros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_seguro_7", null, "CARD", "", 8, "seguro_de_vida_individual_o_familar", "Seguro de Vida Individual o Familiar", "Solicitar Información →", "tel:00000000", "sec_seguros", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true }
            )

            for (b in seedBlocks) {
                val existing = localDb.contentDao().getBlockById(b.id)
                if (existing == null) {
                    localDb.contentDao().insertBlock(b)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_blocks").document(b.id).set(b)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed seguro block to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureSegurosBlocksSynced: ${e.message}")
        }
    }

    private fun ensureRemesasBlocksSynced() {
        try {
            val seedBlocks = listOf(
                ContentBlockEntity("block_remesas_header", null, "CONTAINER", "Recibe tu dinero de forma segura, rápida y sin complicaciones a través de nuestra red de remesadoras aliadas.", 1, "remesa", "Remesas", "Buscar Agencia", "sec_agencias", "sec_remesas", "#173789", "#173789", "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_remesas_aliadas", null, "IMAGE", "Red de remesadoras aliadas oficiales.", 2, "remesadoras_afiliadas2", "Remesadoras Aliadas", null, null, "sec_remesas", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_remesas_dirigidas", null, "CARD", "En caso de fallecimiento en el extranjero, te ofrecemos el BENEFICIO DE REPATRIACIÓN, garantizando que tu último viaje sea de regreso a casa, sin costo alguno para tu familia.", 3, null, "BENEFICIO AL RECIBIR TU REMESA DIRIGIDA A TU CUENTA DISPONIBLE", "Solicitar Información →", "tel:22135580", "sec_remesas", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_rd1", null, "IMAGE", "Asistencia de repatriación para remitente.", 4, "rd1", "Asistencia de repatriación para remitente.", null, null, "sec_remesas", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_rd2", null, "IMAGE", "Asistencia funeraria para remitente (persona en el extranjero).", 5, "rd2", "Asistencia funeraria para remitente (persona en el extranjero).", null, null, "sec_remesas", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_rd3", null, "IMAGE", "Referencias médicas. Información de médicos, farmacias o laboratorios.", 6, "rd3", "Referencias médicas. Información de médicos, farmacias o laboratorios.", null, null, "sec_remesas", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_rd4", null, "IMAGE", "Orientación médica telefónica. Apoyo en interpretación de pruebas de laboratorio y recomendación de medicamentos.", 7, "rd4", "Orientación médica telefónica.", null, null, "sec_remesas", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_rd5", null, "IMAGE", "Teledoctor. Aplicación móvil para consultas médicas.", 8, "rd5", "Teledoctor. Aplicación móvil para consultas médicas.", null, null, "sec_remesas", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true }
            )

            for (b in seedBlocks) {
                val existing = localDb.contentDao().getBlockById(b.id)
                if (existing == null) {
                    localDb.contentDao().insertBlock(b)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_blocks").document(b.id).set(b)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed remesas block to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureRemesasBlocksSynced: ${e.message}")
        }
    }

    private fun ensureServiciosBlocksSynced() {
        try {
            val seedBlocks = listOf(
                ContentBlockEntity("block_servicios_header", null, "CONTAINER", "Tu cooperativa al alcance de tu mano. Gestiona tus finanzas, realiza pagos y solicita servicios desde la comodidad de tu dispositivo.", 1, "logo_composite", "Servicios Digitales", "Descargar", "tel:77957795", "sec_servicios", "#173789", "#173789", "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_servicio_1", null, "CARD", "Lleva el control de tus ahorros y préstamos a donde vayas. Consulta saldos, realiza transferencias entre cuentas y paga servicios de forma rápida y segura.\n\n• Transferencias 24/7\n• Pago de préstamos", 2, "micoope_enlinea", "MICOOPE en línea", "Descargar App", "https://micoope.com.gt", "sec_servicios", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_servicio_2", null, "CARD", "Envía y recibe dinero al instante usando solo el número de teléfono. Pagos rápidos y sin complicaciones con la red Fri.", 3, "logo_fri", "Fri", "Vincular Cuenta", "https://fri.gt", "sec_servicios", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_servicio_3", null, "CARD", "Obtén tu tarjeta de crédito o débito COLUA MICOOPE sin salir de casa. Disfruta de aceptación internacional, beneficios exclusivos y la seguridad que necesitas para tus compras diarias.", 4, "tarjeta_debito", "Solicitud de Tarjetas", "Solicitar Ahora", "tel:77957795", "sec_servicios", null, null, "NORMAL", "NORMAL", "LEFT").apply { isDraft = false; isVisible = true }
            )

            for (b in seedBlocks) {
                val existing = localDb.contentDao().getBlockById(b.id)
                if (existing == null) {
                    localDb.contentDao().insertBlock(b)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_blocks").document(b.id).set(b)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed servicio block to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureServiciosBlocksSynced: ${e.message}")
        }
    }

    private fun ensureBeneficiosBlocksSynced() {
        try {
            val seedBlocks = listOf(
                ContentBlockEntity("block_beneficios_header", null, "CONTAINER", "Para realizar el reclamo de tus beneficios deberás acercarte a tu agencia más cercana y realizar el proceso correspondiente.", 1, "obten_tus_seis_beneficios", "BENEFICIOS COLUA MICOOPE", null, null, "sec_beneficios", "#173789", "#173789", "NORMAL", "BOLD", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_beneficio_1", null, "CARD", "La cooperativa apoya económicamente al asociado en caso de que sea internado en un hospital público o privado, por enfermedad o accidente, calculando el pago según el monto de sus ahorros (1 a 69 años inclusive).", 2, "renta_diaria", "Renta Diaria por Hospitalización", null, null, "sec_beneficios", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_beneficio_2", null, "CARD", "La cooperativa otorgará al asociado un apoyo económico para los gastos médicos incurridos por alguna cirugía como consecuencia de una enfermedad o accidente. (Este beneficio es de por vida, siempre y cuando se asocie en la edad de 1 a 70 años).", 3, "apoyo_quirurgico", "Apoyo Quirúrgico", null, null, "sec_beneficios", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_beneficio_3", null, "CARD", "En caso de fallecimiento, la cooperativa apoya a la familia del asociado con un sepelio digno, proporcionándoles un ataúd fúnebre en coordinación con la red de funerarias autorizadas. (Este beneficio es de por vida, siempre y cuando se asocie en la edad de 1 a 68 años inclusive).", 4, "servicio_funerario", "Servicio Funerario", null, null, "sec_beneficios", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_beneficio_4", null, "CARD", "En caso de fallecimiento del asociado, la cooperativa garantiza la devolución de los ahorros a sus beneficiarios, así mismo se hace entrega del seguro sobre su dinero depositado en sus cuentas, hasta un monto máximo de Q150,000.00. (Este beneficio es de por vida, siempre y cuando se asocie en la edad de 1 a 68 años inclusive).", 5, "beneficio_de_ahorrantes", "Seguro de Ahorrantes", null, null, "sec_beneficios", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_beneficio_5", null, "CARD", "En caso de fallecimiento del asociado con crédito vigente, la cooperativa ofrece un seguro que cubre los saldos insolutos hasta un monto máximo de Q200,000.00 (Asociados de 18 a 69 años inclusive).", 6, "beneficio_de_deudores", "Seguro de Deudores", null, null, "sec_beneficios", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true },
                ContentBlockEntity("block_beneficio_6", null, "CARD", "La cooperativa brinda un apoyo económico a los asociados mayores de 70 años que sean diagnosticados por una enfermedad grave de acuerdo con el catálogo, a través de un único desembolso y cumpliendo con los requisitos establecidos.", 7, "beneficio_de_oro", "Beneficio de Oro", null, null, "sec_beneficios", null, null, "NORMAL", "NORMAL", "CENTER").apply { isDraft = false; isVisible = true }
            )

            for (b in seedBlocks) {
                val existing = localDb.contentDao().getBlockById(b.id)
                if (existing == null) {
                    localDb.contentDao().insertBlock(b)
                    if (isCloudEnabled()) {
                        try {
                            firestore.collection("content_blocks").document(b.id).set(b)
                        } catch (e: Exception) {
                            Log.e("REPO", "Error uploading seed beneficio block to cloud: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error in ensureBeneficiosBlocksSynced: ${e.message}")
        }
    }

    fun getBlocksBySection(sectionId: String): List<ContentBlockEntity> {
        val cleanId = sectionId.lowercase(Locale.getDefault())
        if (cleanId == "sec_nosotros" || cleanId == "nosotros") {
            ensureNosotrosBlocksSynced()
        }
        if (cleanId == "sec_ahorros" || cleanId == "ahorros") {
            ensureAhorrosBlocksSynced()
        }
        if (cleanId == "sec_creditos" || cleanId == "creditos") {
            ensureCreditosBlocksSynced()
        }
        if (cleanId == "sec_seguros" || cleanId == "seguros") {
            ensureSegurosBlocksSynced()
        }
        if (cleanId == "sec_remesas" || cleanId == "remesas") {
            ensureRemesasBlocksSynced()
        }
        if (cleanId == "sec_servicios" || cleanId == "servicios") {
            ensureServiciosBlocksSynced()
        }
        if (cleanId == "sec_beneficios" || cleanId == "beneficios") {
            ensureBeneficiosBlocksSynced()
        }

        val altId = if (cleanId.startsWith("sec_")) cleanId.replace("sec_", "") else "sec_$cleanId"

        val local = localDb.contentDao().getBlocksBySection(cleanId).toMutableList()
        val altLocal = localDb.contentDao().getBlocksBySection(altId)

        for (b in altLocal) {
            if (local.none { it.id == b.id }) {
                local.add(b)
            }
        }

        if (isCloudEnabled()) {
            try {
                val task = firestore.collection("content_blocks").whereIn("sectionId", listOf(cleanId, altId)).get()
                val cloud = await(task)?.toObjects(ContentBlockEntity::class.java)
                if (cloud != null && cloud.isNotEmpty()) {
                    cloud.forEach { cloudBlock ->
                        localDb.contentDao().insertBlock(cloudBlock)
                        if (local.none { it.id == cloudBlock.id }) {
                            local.add(cloudBlock)
                        } else {
                            val idx = local.indexOfFirst { it.id == cloudBlock.id }
                            if (idx >= 0) local[idx] = cloudBlock
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("REPO", "Error syncing blocks from Firestore: ${e.message}")
            }
        }

        if (cleanId == "sec_noticias" || cleanId == "noticias") {
            return local.sortedByDescending { it.updatedAt }
        }

        return local.sortedBy { it.displayOrder }
    }

    fun getPublishedBlocksBySection(sectionId: String): List<ContentBlockEntity> {
        return getBlocksBySection(sectionId).filter { !it.isDraft && it.isVisible }
    }

    fun getBlocksByItem(itemId: String): List<ContentBlockEntity> {
        val local = localDb.contentDao().getBlocksByItem(itemId)
        if (!isCloudEnabled()) return local
        val task = firestore.collection("content_blocks").whereEqualTo("contentItemId", itemId).orderBy("displayOrder").get()
        val cloud = await(task)?.toObjects(ContentBlockEntity::class.java)
        return if (cloud != null && cloud.isNotEmpty()) cloud else local
    }

    fun insertBlock(block: ContentBlockEntity) {
        if (block.id.isEmpty()) block.id = UUID.randomUUID().toString()
        localDb.contentDao().insertBlock(block)
        if (isCloudEnabled()) firestore.collection("content_blocks").document(block.id).set(block)
    }

    fun getBlockById(blockId: String): ContentBlockEntity? {
        return localDb.contentDao().getBlockById(blockId)
    }

    fun deleteBlockById(blockId: String) {
        localDb.contentDao().deleteBlockById(blockId)
        if (isCloudEnabled()) firestore.collection("content_blocks").document(blockId).delete()
    }

    // --- AGENCIAS ---
    fun getAllAgencias(): List<AgenciaEntity> {
        val local = localDb.agenciaDao().getAllAgencias()
        if (local.isNotEmpty()) return local.sortedBy { it.departamento }
        if (!isCloudEnabled()) return local
        val task = firestore.collection("agencias").get()
        val docs = await(task)?.documents ?: return local
        val list = docs.mapNotNull { doc ->
            doc.toObject(AgenciaEntity::class.java)?.apply { if (id.isEmpty()) id = doc.id }
        }
        if (list.isNotEmpty()) {
            list.forEach { localDb.agenciaDao().insertAll(it) }
            return list.sortedBy { it.departamento }
        }
        return local
    }

    fun getAgenciaById(id: String): AgenciaEntity? {
        val local = localDb.agenciaDao().getAgenciaById(id)
        if (!isCloudEnabled()) return local
        val task = firestore.collection("agencias").document(id).get()
        return await(task)?.toObject(AgenciaEntity::class.java) ?: local
    }

    fun insertAgencias(vararg agencias: AgenciaEntity) {
        localDb.agenciaDao().insertAll(*agencias)
        if (isCloudEnabled()) {
            agencias.forEach { a -> firestore.collection("agencias").document(a.id).set(a) }
        }
    }

    fun purgarAgenciasDuplicadas(callback: () -> Unit = {}) {
        Thread {
            try {
                if (!isCloudEnabled()) {
                    callback()
                    return@Thread
                }

                val task = firestore.collection("agencias").get()
                val snapshot = await(task) ?: return@Thread
                val docs = snapshot.documents

                val seen = HashSet<String>()
                val toDelete = ArrayList<DocumentReference>()

                for (doc in docs) {
                    val nombre = doc.getString("nombre") ?: ""
                    val depto = doc.getString("departamento") ?: ""
                    val key = "${nombre.trim().lowercase(Locale.getDefault())}_${depto.trim().lowercase(Locale.getDefault())}"

                    if (key.length > 1 && seen.contains(key)) {
                        toDelete.add(doc.reference)
                    } else if (key.length > 1) {
                        seen.add(key)
                    }
                }

                if (toDelete.isNotEmpty()) {
                    Log.i("REPO", "Eliminando ${toDelete.size} agencias duplicadas en Firestore...")
                    for (ref in toDelete) {
                        ref.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("REPO", "Error al purgar agencias duplicadas: ${e.message}")
            } finally {
                callback()
            }
        }.start()
    }

    fun deleteAgencia(agencia: AgenciaEntity) {
        localDb.agenciaDao().delete(agencia)
        if (isCloudEnabled()) firestore.collection("agencias").document(agencia.id).delete()
    }

    // --- NAVEGACIÓN (Navbar, Sidebar, BottomNav) ---
    fun getVisibleNavigation(type: String): List<NavigationItemEntity> {
        val publishedSectionIds = localDb.sectionDao().getPublishedSections().map { it.id }.toSet()
        var local = localDb.navigationDao().getVisibleItemsByType(type)
        if (local.isEmpty()) {
            DataSeeder.seedIfEmpty(context)
            local = localDb.navigationDao().getVisibleItemsByType(type)
        }
        return local.filter { item ->
            !item.targetSectionId.startsWith("sec_") || publishedSectionIds.contains(item.targetSectionId)
        }
    }

    fun getRobustSidebarItems(): List<NavigationItemEntity> {
        val publishedSectionIds = localDb.sectionDao().getPublishedSections().map { it.id }.toSet()
        var items = localDb.navigationDao().getVisibleItemsByType("SIDEBAR").toMutableList()
        if (items.isEmpty()) {
            items = localDb.navigationDao().getVisibleItemsByType("SIDE_MENU").toMutableList()
        }
        if (items.isEmpty()) {
            DataSeeder.seedIfEmpty(context, true)
            items = localDb.navigationDao().getVisibleItemsByType("SIDEBAR").toMutableList()
        }
        return items.filter { item ->
            !item.targetSectionId.startsWith("sec_") || publishedSectionIds.contains(item.targetSectionId)
        }.sortedBy { it.displayOrder }
    }

    fun insertNavigationItem(item: NavigationItemEntity) {
        if (item.id.isEmpty()) item.id = UUID.randomUUID().toString()
        localDb.navigationDao().insert(item)
        if (isCloudEnabled()) firestore.collection("navigation_items").document(item.id).set(item)
    }

    // --- REGISTRO Y GESTIÓN DE USUARIOS Y DISPOSITIVOS (Firestore) ---
    private fun normalizeDpi(dpi: String): String {
        return dpi.replace(Regex("[^0-9a-zA-Z]"), "").lowercase(Locale.getDefault())
    }

    fun formatDpi(raw: String): String {
        val digits = raw.replace(Regex("\\D"), "")
        if (digits.length != 13) return raw
        return "${digits.substring(0, 4)} ${digits.substring(4, 9)} ${digits.substring(9, 13)}"
    }

    fun validateDpi(raw: String): Boolean {
        val digits = normalizeDpi(raw)
        return digits.length == 13
    }

    fun validatePhone(raw: String): Boolean {
        val digits = raw.replace(Regex("\\D"), "")
        return digits.length == 8
    }

    fun formatPhone(raw: String): String {
        return raw.replace(Regex("\\D"), "")
    }

    private fun getNextUserId(callback: (String) -> Unit, onError: (Exception) -> Unit = {}) {
        val counterRef = firestore.collection("systemCounters").document("usuarios")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            var lastNum = -1L
            if (snapshot.exists()) {
                lastNum = snapshot.getLong("lastAssignedNumber") ?: -1L
            } else {
                lastNum = -1L
            }
            val newNum = lastNum + 1L
            transaction.set(counterRef, hashMapOf("lastAssignedNumber" to newNum), SetOptions.merge())
            String.format(Locale.getDefault(), "%07d", newNum)
        }.addOnSuccessListener { userId ->
            Log.i("FIRESTORE_COUNTER", "Generated ascending associate userId: $userId")
            callback(userId)
        }.addOnFailureListener { e ->
            Log.e("FIRESTORE_COUNTER", "Error getting next user id: ${e.message}", e)
            onError(e)
        }
    }

    private fun getNextGuestId(callback: (String) -> Unit, onError: (Exception) -> Unit = {}) {
        val counterRef = firestore.collection("systemCounters").document("invitados")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            var lastNum = 10000000L
            if (snapshot.exists()) {
                lastNum = snapshot.getLong("lastAssignedGuestNumber") ?: 10000000L
            } else {
                lastNum = 10000000L
            }
            val newNum = lastNum - 1L
            transaction.set(counterRef, hashMapOf("lastAssignedGuestNumber" to newNum), SetOptions.merge())
            String.format(Locale.getDefault(), "%07d", newNum)
        }.addOnSuccessListener { guestId ->
            Log.i("FIRESTORE_COUNTER", "Generated descending guest userId: $guestId")
            callback(guestId)
        }.addOnFailureListener { e ->
            Log.e("FIRESTORE_COUNTER", "Error getting guest id: ${e.message}", e)
            callback("9999999")
        }
    }

    fun ensureFirebaseAuth(onAuthReady: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.i("FIREBASE_AUTH", "Autenticación anónima exitosa")
                    onAuthReady()
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_AUTH", "Error en autenticación anónima: ${e.message}")
                    onAuthReady()
                }
        } else {
            onAuthReady()
        }
    }

    fun crearPerfilUsuarioFirestore(
        uid: String,
        nombre: String,
        telefono: String,
        rawDpi: String,
        email: String,
        esInvitado: Boolean,
        callback: (Boolean, String, String?) -> Unit
    ) {
        if (!isCloudEnabled()) {
            callback(false, "", "Conexión a la nube requerida para registro.")
            return
        }

        getInstallationId { installId ->
            if (esInvitado) {
                // Invitado: usamos el UID de Firebase directamente como su ID local
                val guestId = uid
                val profileData = linkedMapOf<String, Any>(
                    "firebaseUid" to uid,
                    "userId" to guestId,
                    "tipoUsuario" to "INVITADO",
                    "installationId" to installId,
                    "fechaRegistro" to FieldValue.serverTimestamp(),
                    "ultimaActividad" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 3
                )
                firestore.collection("usuarios").document(guestId).set(profileData, SetOptions.merge())
                    .addOnSuccessListener {
                        saveLocalUser(guestId, "", "Invitado", "", "", "", "GUEST")
                        upsertDeviceSubcollection(guestId, "INVITADO", installId)
                        callback(true, guestId, null)
                    }
                    .addOnFailureListener { e ->
                        callback(false, "", e.message)
                    }
                return@getInstallationId
            }

            // Flujo de Asociado: ID consecutivo 7 dígitos
            val counterRef = firestore.collection("systemCounters").document("users")
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(counterRef)
                val lastNum = if (snapshot.exists()) {
                    snapshot.getLong("lastAssignedNumber") ?: 0L
                } else 0L
                
                val newNum = lastNum + 1L
                transaction.set(counterRef, hashMapOf("lastAssignedNumber" to newNum), SetOptions.merge())
                
                val formattedId = String.format(Locale.getDefault(), "%07d", newNum)
                Pair(newNum, formattedId)
            }.addOnSuccessListener { pair ->
                val idNumerico = pair.first
                val formattedId = pair.second
                
                val normalizedDpi = normalizeDpi(rawDpi)
                val formattedDpi = formatDpi(rawDpi)
                val cleanPhone = formatPhone(telefono)
                val telefonoCompleto = "+502$cleanPhone"

                val isAdminEmail = email.trim().equals("coluarl@gmail.com", ignoreCase = true)
                val tipoUsuarioFinal = if (isAdminEmail) "ADMIN" else "ASOCIADO"
                val roleFinal = if (isAdminEmail) "ADMIN" else "MEMBER"

                val profileData = linkedMapOf<String, Any>(
                    "firebaseUid" to uid,
                    "userId" to formattedId,
                    "idNumerico" to idNumerico,
                    "tipoUsuario" to tipoUsuarioFinal,
                    "nombre" to nombre,
                    "dpi" to formattedDpi,
                    "dpiNormalizado" to normalizedDpi,
                    "telefono" to cleanPhone,
                    "telefonoCompleto" to telefonoCompleto,
                    "email" to email,
                    "estadoCuenta" to "ACTIVA",
                    "installationId" to installId,
                    "fechaRegistro" to FieldValue.serverTimestamp(),
                    "ultimaActividad" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 3
                )
                
                // Guardamos usando formattedId ("0000001", "0000002"...) como ID de documento
                firestore.collection("usuarios").document(formattedId).set(profileData)
                    .addOnSuccessListener {
                        saveLocalUser(formattedId, formattedDpi, nombre, cleanPhone, email, "", roleFinal)
                        upsertDeviceSubcollection(formattedId, tipoUsuarioFinal, installId)
                        callback(true, formattedId, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FIREBASE_AUTH", "Error guardando perfil: ${e.message}")
                        callback(false, "", "Error guardando perfil: ${e.message}")
                    }
            }.addOnFailureListener { e ->
                Log.e("FIREBASE_AUTH", "Error asignando ID consecutivo: ${e.message}")
                callback(false, "", "Error asignando ID consecutivo: ${e.message}")
            }
        }
    }

    fun obtenerPerfilUsuarioFirestore(
        uid: String,
        email: String,
        callback: (Boolean, Map<String, String>?, String?) -> Unit
    ) {
        if (!isCloudEnabled()) {
            callback(false, null, "Conexión a la nube requerida para iniciar sesión.")
            return
        }

        val cleanEmail = email.trim()
        val isAdminEmail = cleanEmail.equals("coluarl@gmail.com", ignoreCase = true)

        // 1. Buscamos el perfil por firebaseUid
        firestore.collection("usuarios").whereEqualTo("firebaseUid", uid).get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val doc = query.documents[0]
                    processUserProfileDoc(doc, uid, cleanEmail, isAdminEmail, callback)
                } else {
                    // 2. Si no se encontró por firebaseUid, buscar por email registrado
                    firestore.collection("usuarios").whereEqualTo("email", cleanEmail).get()
                        .addOnSuccessListener { queryEmail ->
                            if (!queryEmail.isEmpty) {
                                val doc = queryEmail.documents[0]
                                doc.reference.update("firebaseUid", uid)
                                processUserProfileDoc(doc, uid, cleanEmail, isAdminEmail, callback)
                            } else {
                                if (isAdminEmail) {
                                    crearPerfilAdminOficial(uid, cleanEmail, callback)
                                } else {
                                    callback(false, null, "No se encontró el perfil de usuario asociado a $cleanEmail.")
                                }
                            }
                        }
                        .addOnFailureListener {
                            if (isAdminEmail) {
                                crearPerfilAdminOficial(uid, cleanEmail, callback)
                            } else {
                                callback(false, null, "No se encontró el perfil de usuario asociado a $cleanEmail.")
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE_AUTH", "Error obteniendo perfil: ${e.message}")
                if (isAdminEmail) {
                    crearPerfilAdminOficial(uid, cleanEmail, callback)
                } else {
                    callback(false, null, "Error obteniendo perfil: ${e.message}")
                }
            }
    }

    private fun processUserProfileDoc(
        doc: DocumentSnapshot,
        uid: String,
        email: String,
        isAdminEmail: Boolean,
        callback: (Boolean, Map<String, String>?, String?) -> Unit
    ) {
        val userId = doc.id
        val nombre = doc.getString("nombre") ?: if (isAdminEmail) "Administrador COLUA" else "Asociado COLUA"
        val telefono = doc.getString("telefono") ?: ""
        var tipoUsuario = doc.getString("tipoUsuario") ?: doc.getString("role") ?: "ASOCIADO"

        if (isAdminEmail && tipoUsuario != "ADMIN") {
            tipoUsuario = "ADMIN"
            doc.reference.update("tipoUsuario", "ADMIN", "role", "ADMIN")
        }

        val role = if (tipoUsuario.equals("ADMIN", ignoreCase = true) || tipoUsuario.equals("SUPER_ADMIN", ignoreCase = true)) "ADMIN" else if (tipoUsuario.equals("INVITADO", ignoreCase = true) || tipoUsuario.equals("GUEST", ignoreCase = true)) "GUEST" else "MEMBER"

        saveLocalUser(userId, doc.getString("dpi") ?: "", nombre, telefono, email, "", role)

        getInstallationId { installId ->
            upsertDeviceSubcollection(userId, tipoUsuario, installId)
        }

        val result = mapOf(
            "userId" to userId,
            "nombre" to nombre,
            "telefono" to telefono,
            "role" to role
        )
        callback(true, result, null)
    }

    private fun crearPerfilAdminOficial(
        uid: String,
        email: String,
        callback: (Boolean, Map<String, String>?, String?) -> Unit
    ) {
        getInstallationId { installId ->
            val adminId = "admin_01"
            val profileData = linkedMapOf<String, Any>(
                "firebaseUid" to uid,
                "userId" to adminId,
                "tipoUsuario" to "ADMIN",
                "nombre" to "Administrador COLUA",
                "email" to email,
                "dpi" to "0000000000000",
                "telefono" to "77957795",
                "estadoCuenta" to "ACTIVA",
                "installationId" to installId,
                "fechaRegistro" to FieldValue.serverTimestamp(),
                "ultimaActividad" to FieldValue.serverTimestamp(),
                "schemaVersion" to 3
            )

            firestore.collection("usuarios").document(adminId).set(profileData, SetOptions.merge())
                .addOnSuccessListener {
                    saveLocalUser(adminId, "0000000000000", "Administrador COLUA", "77957795", email, "", "ADMIN")
                    upsertDeviceSubcollection(adminId, "ADMIN", installId)
                    val result = mapOf(
                        "userId" to adminId,
                        "nombre" to "Administrador COLUA",
                        "telefono" to "77957795",
                        "role" to "ADMIN"
                    )
                    callback(true, result, null)
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_AUTH", "Error creando perfil admin oficial: ${e.message}")
                    callback(false, null, "Error creando perfil admin oficial: ${e.message}")
                }
        }
    }

    fun cambiarRolUsuario(
        userId: String,
        nuevoTipoUsuario: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!isCloudEnabled()) {
            callback(false, "Conexión a la nube requerida para actualizar roles.")
            return
        }

        val nuevoRole = if (nuevoTipoUsuario == "ADMIN") "ADMIN" else if (nuevoTipoUsuario == "INVITADO") "GUEST" else "MEMBER"
        val docRef = firestore.collection("usuarios").document(userId)

        docRef.update(
            mapOf(
                "tipoUsuario" to nuevoTipoUsuario,
                "role" to nuevoRole
            )
        ).addOnSuccessListener {
            Log.i("REPO_ROLE", "Rol de usuario $userId cambiado exitosamente a $nuevoTipoUsuario")
            callback(true, null)
        }.addOnFailureListener { e ->
            Log.e("REPO_ROLE", "Error actualizando rol de usuario $userId: ${e.message}")
            callback(false, e.message)
        }
    }

    @JvmOverloads
    fun registrarUsuarioReal(nombre: String, telefono: String, rawDpi: String, esInvitado: Boolean, callback: (Boolean, String, String?) -> Unit = { _, _, _ -> }) {
        ensureFirebaseAuth {
            proceedWithRegistration(nombre, telefono, rawDpi, esInvitado, callback)
        }
    }

    private fun upsertDeviceSubcollection(userId: String, tipoUsuario: String, installId: String) {
        val deviceData = linkedMapOf<String, Any>(
            "installationId" to installId,
            "userId" to userId,
            "tipoUsuario" to tipoUsuario,
            "modeloTelefono" to Build.MODEL,
            "plataforma" to "ANDROID",
            "versionApp" to "1.0.0",
            "fechaRegistro" to FieldValue.serverTimestamp(),
            "ultimaActividad" to FieldValue.serverTimestamp(),
            "estado" to "ACTIVO"
        )
        firestore.collection("usuarios").document(userId)
            .collection("dispositivos").document(installId)
            .set(deviceData, SetOptions.merge())
            .addOnSuccessListener {
                Log.i("DEVICE_REG", "Dispositivo $installId registrado correctamente bajo usuarios/$userId/dispositivos")
            }
    }

    @JvmOverloads
    fun actualizarPerfil(nombre: String, telefono: String, rawDpi: String, callback: (Boolean, String?) -> Unit = { _, _ -> }) {
        val pref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = pref.getString("user_id", "") ?: ""
        val role = pref.getString("user_role", "MEMBER") ?: "MEMBER"

        if (!validateDpi(rawDpi) || !validatePhone(telefono) || nombre.length < 3) {
            callback(false, "Datos inválidos (Nombre >= 3, DPI = 13 dígitos, Teléfono = 8 dígitos).")
            return
        }

        if ("GUEST".equals(role, ignoreCase = true) || userId.startsWith("guest_")) {
            Log.i("PROFILE", "Convirtiendo cuenta de invitado ($userId) a Asociado mediante actualización de perfil...")
            registrarUsuarioReal(nombre, telefono, rawDpi, false) { success, newId, errorMsg ->
                callback(success, errorMsg)
            }
            return
        }

        val targetUserId = if (userId.isNotEmpty()) userId.removePrefix("user") else "0000000"
        val formattedDpi = formatDpi(rawDpi)
        val normalizedDpi = normalizeDpi(rawDpi)
        val cleanPhone = formatPhone(telefono)
        val telefonoCompleto = "+502$cleanPhone"

        val userEmail = pref.getString("user_email", "") ?: ""
        saveLocalUser(targetUserId, formattedDpi, nombre, cleanPhone, userEmail, "", pref.getString("user_role", "MEMBER") ?: "MEMBER")

        if (!isCloudEnabled()) {
            callback(true, null)
            return
        }

        ensureFirebaseAuth {
            val updateData = linkedMapOf<String, Any>(
                "userId" to targetUserId,
                "tipoUsuario" to "ASOCIADO",
                "nombre" to nombre,
                "dpi" to formattedDpi,
                "dpiNormalizado" to normalizedDpi,
                "telefono" to cleanPhone,
                "telefonoCompleto" to telefonoCompleto,
                "ultimaActividad" to FieldValue.serverTimestamp(),
                "schemaVersion" to 2
            )

            firestore.collection("usuarios").document(targetUserId)
                .set(updateData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.i("PROFILE_UPDATE", "PROFILE_UPDATE_SUCCESS")
                    getInstallationId { installId ->
                        upsertDeviceSubcollection(targetUserId, "ASOCIADO", installId)
                    }
                    callback(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e("PROFILE_UPDATE", "PROFILE_UPDATE_FAILURE: ${e.message}", e)
                    callback(true, null)
                }
        }
    }

    private fun proceedWithRegistration(nombre: String, telefono: String, rawDpi: String, esInvitado: Boolean, callback: (Boolean, String, String?) -> Unit) {
        getInstallationId { installId ->
            Log.i("FIRESTORE_REG", "Iniciando registro instantáneo. EsInvitado: $esInvitado, InstallId: $installId")

            try {
                if (esInvitado) {
                    getNextGuestId({ guestId ->
                        saveLocalUser(guestId, "", "Invitado", "", "", "", "GUEST")
                        callback(true, guestId, null)

                        if (isCloudEnabled()) {
                            ensureFirebaseAuth {
                                val profileData = linkedMapOf<String, Any>(
                                    "userId" to guestId,
                                    "tipoUsuario" to "INVITADO",
                                    "installationId" to installId,
                                    "fechaRegistro" to FieldValue.serverTimestamp(),
                                    "ultimaActividad" to FieldValue.serverTimestamp(),
                                    "schemaVersion" to 2
                                )
                                firestore.collection("usuarios").document(guestId).set(profileData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        upsertDeviceSubcollection(guestId, "INVITADO", installId)
                                    }
                            }
                        }
                    }, { e ->
                        val fallbackGuest = "9999999"
                        saveLocalUser(fallbackGuest, "", "Invitado", "", "", "", "GUEST")
                        callback(true, fallbackGuest, null)
                    })
                } else {
                    if (!validateDpi(rawDpi) || !validatePhone(telefono) || nombre.length < 3) {
                        callback(false, "", "Datos de asociado inválidos (Nombre >= 3, DPI = 13 dígitos, Teléfono = 8 dígitos).")
                        return@getInstallationId
                    }

                    val normalizedDpi = normalizeDpi(rawDpi)
                    val formattedDpi = formatDpi(rawDpi)
                    val cleanPhone = formatPhone(telefono)
                    val telefonoCompleto = "+502$cleanPhone"

                    if (!isCloudEnabled()) {
                        val fallbackId = "user0000000"
                        saveLocalUser(fallbackId, formattedDpi, nombre, cleanPhone, "", "", "MEMBER")
                        callback(true, fallbackId, null)
                        return@getInstallationId
                    }

                    firestore.collection("usuarios")
                        .whereEqualTo("dpiNormalizado", normalizedDpi)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            if (querySnapshot != null && !querySnapshot.isEmpty) {
                                val existingDoc = querySnapshot.documents[0]
                                val existingUserId = existingDoc.getString("userId") ?: existingDoc.id
                                val cleanExistingUserId = existingUserId.removePrefix("user")
                                updateExistingUser(cleanExistingUserId, nombre, telefono, rawDpi, normalizedDpi, installId, callback)
                            } else {
                                allocateNewUserAndSave("ASOCIADO", nombre, formatDpi(rawDpi), normalizedDpi, formatPhone(telefono), "+502" + formatPhone(telefono), installId, callback)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.w("FIRESTORE_REG", "Fallo consulta DPI en nube (creando local): ${e.message}")
                            val fallbackId = "user0000000"
                            saveLocalUser(fallbackId, formattedDpi, nombre, cleanPhone, "", "", "MEMBER")
                            callback(true, fallbackId, null)
                        }
                }
            } catch (e: Exception) {
                Log.w("FIRESTORE_REG", "Excepción en registro: ${e.message}")
                val fallbackId = if (esInvitado) "guest_local" else "user0000000"
                saveLocalUser(fallbackId, rawDpi, nombre, telefono, "", "", if (esInvitado) "GUEST" else "MEMBER")
                callback(true, fallbackId, null)
            }
        }
    }

    private fun updateExistingUser(userId: String, nombre: String, telefono: String, rawDpi: String, normalizedDpi: String, installId: String, callback: (Boolean, String, String?) -> Unit) {
        val cleanUserId = userId.removePrefix("user")
        val formattedDpi = formatDpi(rawDpi)
        val cleanPhone = formatPhone(telefono)
        val telefonoCompleto = "+502$cleanPhone"

        val updateData = linkedMapOf<String, Any>(
            "userId" to cleanUserId,
            "tipoUsuario" to "ASOCIADO",
            "dpi" to formattedDpi,
            "dpiNormalizado" to normalizedDpi,
            "nombre" to nombre,
            "telefono" to cleanPhone,
            "telefonoCompleto" to telefonoCompleto,
            "ultimaActividad" to FieldValue.serverTimestamp(),
            "estadoCuenta" to "ACTIVA",
            "schemaVersion" to 2
        )
        updateDeviceAndSession(cleanUserId, cleanPhone, formattedDpi, nombre, "MEMBER", installId)

        firestore.collection("usuarios").document(cleanUserId)
            .set(updateData, SetOptions.merge())
            .addOnSuccessListener {
                Log.i("FIRESTORE_REG", "Usuario existente actualizado (fechaRegistro conservada): $cleanUserId")
                callback(true, cleanUserId, null)
            }
            .addOnFailureListener { e ->
                Log.e("FIRESTORE_REG", "Error actualizando usuario existente: ${e.message}")
                callback(true, cleanUserId, null)
            }
    }

    private fun allocateNewUserAndSave(
        tipoUsuario: String,
        nombre: String?,
        dpi: String?,
        dpiNormalizado: String?,
        telefono: String?,
        telefonoCompleto: String?,
        installId: String,
        callback: (Boolean, String, String?) -> Unit
    ) {
        val counterRef = firestore.collection("systemCounters").document("usuarios")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            var lastNum = -1L
            if (snapshot.exists()) {
                lastNum = snapshot.getLong("lastAssignedNumber") ?: -1L
            }
            val newNum = lastNum + 1L
            transaction.set(counterRef, hashMapOf("lastAssignedNumber" to newNum), SetOptions.merge())
            
            val formattedId = String.format(Locale.getDefault(), "user%07d", newNum)
            Pair(newNum, formattedId)
        }.addOnSuccessListener { pair ->
            val idNumerico = pair.first
            val formattedId = pair.second

            val userData = linkedMapOf<String, Any>(
                "userId" to formattedId,
                "idNumerico" to idNumerico,
                "tipoUsuario" to tipoUsuario,
                "estado" to "ACTIVO",
                "installationId" to installId,
                "fechaRegistro" to FieldValue.serverTimestamp(),
                "ultimaActividad" to FieldValue.serverTimestamp(),
                "schemaVersion" to 2
            )
            if (nombre != null) userData["nombre"] = nombre
            if (dpi != null) userData["dpi"] = dpi
            if (dpiNormalizado != null) userData["dpiNormalizado"] = dpiNormalizado
            if (telefono != null) userData["telefono"] = telefono
            if (telefonoCompleto != null) userData["telefonoCompleto"] = telefonoCompleto

            updateDeviceAndSession(formattedId, telefono ?: "", dpi ?: "", nombre ?: "Invitado", if ("ASOCIADO".equals(tipoUsuario)) "MEMBER" else "GUEST", installId)

            firestore.collection("usuarios").document(formattedId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.i("REG", "Nuevo usuario creado con ID numerico: $formattedId para installId: $installId")
                    upsertDeviceSubcollection(formattedId, tipoUsuario, installId)
                    callback(true, formattedId, null)
                }
                .addOnFailureListener { e ->
                    Log.e("REG", "Error guardando usuario: ${e.message}")
                    callback(false, "", e.message)
                }
        }.addOnFailureListener { e ->
            Log.e("REG", "Error en transacción de contador atómico: ${e.message}")
            callback(false, "", e.message)
        }
    }

    private fun updateDeviceAndSession(userId: String, phone: String, dpi: String, name: String, role: String, installId: String) {
        val pref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        pref.edit()
            .putString("user_id", userId)
            .putString("user_dpi", dpi)
            .putString("user_name", name)
            .putString("user_phone", phone)
            .putString("user_role", role)
            .apply()

        Thread {
            val localUser = UserEntity(userId, dpi, name, phone, role)
            localDb.userDao().insert(localUser)
        }.start()

        val deviceData = hashMapOf<String, Any>(
            "installationId" to installId,
            "modeloDispositivo" to Build.MODEL,
            "fabricante" to Build.MANUFACTURER,
            "ultimaActividad" to FieldValue.serverTimestamp(),
            "activo" to true
        )
        firestore.collection("usuarios").document(userId)
            .collection("dispositivos").document(installId)
            .set(deviceData, SetOptions.merge())
    }

    fun purgarUsuariosDuplicados() {
        Log.i("REPO", "purgarUsuariosDuplicados omitido para proteger usuarios reales de Firestore.")
    }

    @JvmOverloads
    fun saveLocalUser(userId: String, dpi: String, name: String, phone: String, email: String = "", password: String = "", role: String) {
        val pref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val cleanUserId = userId.removePrefix("user")
        pref.edit()
            .putString("user_id", cleanUserId)
            .putString("user_dpi", dpi)
            .putString("user_name", name)
            .putString("user_phone", phone)
            .putString("user_email", email)
            .putString("user_role", role)
            .apply()

        Thread {
            val localUser = UserEntity(cleanUserId, dpi, name, phone, email, password, role)
            localDb.userDao().insert(localUser)
        }.start()
    }

    @JvmOverloads
    fun actualizarUltimaActividad(context: Activity? = null, idUsuario: String? = null) {
        val pref = context?.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            ?: this.context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val rawUserId = idUsuario ?: pref.getString("user_id", "") ?: ""
        if (rawUserId.isEmpty()) return
        val userId = rawUserId.removePrefix("user")

        if (!isCloudEnabled()) return

        ensureFirebaseAuth {
            val userDocRef = firestore.collection("usuarios").document(userId)
            userDocRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    pref.edit().putString("user_id", userId).apply()
                    performActivityUpdate(userDocRef, userId, pref)
                    return@addOnSuccessListener
                }

                Log.w("REPO", "Documento numerico $userId no encontrado directamente. Buscando formato alternativo user$userId...")

                val altId = "user$userId"

                firestore.collection("usuarios").document(altId).get().addOnSuccessListener { altSnapshot ->
                    if (altSnapshot.exists()) {
                        Log.i("REPO", "Documento encontrado con ID alternativo: $altId. Normalizando sesion a numeric ID...")
                        pref.edit().putString("user_id", userId).apply()
                        val altDocRef = firestore.collection("usuarios").document(altId)
                        performActivityUpdate(altDocRef, userId, pref)
                    } else {
                        val userDpi = pref.getString("user_dpi", "") ?: ""
                        val normalizedDpi = normalizeDpi(userDpi)

                        firestore.collection("usuarios").whereEqualTo("userId", userId).get().addOnSuccessListener { qSnap ->
                            if (qSnap != null && !qSnap.isEmpty) {
                                val foundDoc = qSnap.documents[0]
                                val foundId = foundDoc.id.removePrefix("user")
                                Log.i("REPO", "Documento encontrado via campo userId: $foundId")
                                pref.edit().putString("user_id", foundId).apply()
                                performActivityUpdate(foundDoc.reference, foundId, pref)
                            } else if (normalizedDpi.isNotEmpty()) {
                                firestore.collection("usuarios").whereEqualTo("dpiNormalizado", normalizedDpi).get().addOnSuccessListener { dpiSnap ->
                                    if (dpiSnap != null && !dpiSnap.isEmpty) {
                                        val foundDoc = dpiSnap.documents[0]
                                        val foundId = foundDoc.id.removePrefix("user")
                                        Log.i("REPO", "Documento encontrado via dpiNormalizado: $foundId")
                                        pref.edit().putString("user_id", foundId).apply()
                                        performActivityUpdate(foundDoc.reference, foundId, pref)
                                    } else {
                                        restoreOrCreateMissingUserDocument(userId, context, pref)
                                    }
                                }.addOnFailureListener {
                                    restoreOrCreateMissingUserDocument(userId, context, pref)
                                }
                            } else {
                                restoreOrCreateMissingUserDocument(userId, context, pref)
                            }
                        }.addOnFailureListener {
                            restoreOrCreateMissingUserDocument(userId, context, pref)
                        }
                    }
                }.addOnFailureListener {
                    restoreOrCreateMissingUserDocument(userId, context, pref)
                }
            }.addOnFailureListener {
                // Offline fallback
            }
        }
    }

    private fun performActivityUpdate(docRef: DocumentReference, activeUserId: String, pref: SharedPreferences) {
        docRef.update("ultimaActividad", FieldValue.serverTimestamp())
            .addOnFailureListener {
                docRef.set(hashMapOf("ultimaActividad" to FieldValue.serverTimestamp()), SetOptions.merge())
            }

        getInstallationId { installId ->
            val deviceUpdate = hashMapOf<String, Any>(
                "installationId" to installId,
                "ultimaActividad" to FieldValue.serverTimestamp(),
                "estado" to "ACTIVO"
            )
            docRef.collection("dispositivos").document(installId).set(deviceUpdate, SetOptions.merge())
        }
    }

    private fun restoreOrCreateMissingUserDocument(userId: String, context: Activity?, pref: SharedPreferences) {
        val cleanUserId = userId.removePrefix("user")
        val userName = pref.getString("user_name", "") ?: ""
        val userDpi = pref.getString("user_dpi", "") ?: ""
        val userPhone = pref.getString("user_phone", "") ?: ""
        val userRole = pref.getString("user_role", "MEMBER") ?: "MEMBER"

        if (userName.isNotEmpty() && userName != "Invitado") {
            Log.i("REPO", "Restaurando documento de usuario $cleanUserId en Firestore con datos locales...")
            val formattedDpi = formatDpi(userDpi)
            val normalizedDpi = normalizeDpi(userDpi)
            val cleanPhone = formatPhone(userPhone)

            val userData = linkedMapOf<String, Any>(
                "userId" to cleanUserId,
                "nombre" to userName,
                "dpi" to formattedDpi,
                "dpiNormalizado" to normalizedDpi,
                "telefono" to cleanPhone,
                "telefonoCompleto" to "+502$cleanPhone",
                "tipoUsuario" to if ("GUEST".equals(userRole, ignoreCase = true)) "INVITADO" else "ASOCIADO",
                "estado" to "ACTIVO",
                "fechaRegistro" to FieldValue.serverTimestamp(),
                "ultimaActividad" to FieldValue.serverTimestamp(),
                "schemaVersion" to 2
            )

            pref.edit().putString("user_id", cleanUserId).apply()
            val targetRef = firestore.collection("usuarios").document(cleanUserId)
            targetRef.set(userData, SetOptions.merge()).addOnSuccessListener {
                performActivityUpdate(targetRef, cleanUserId, pref)
            }
        } else if ("GUEST".equals(userRole, ignoreCase = true) || userName == "Invitado") {
            Log.i("REPO", "Restaurando usuario invitado $cleanUserId en Firestore...")
            val userData = linkedMapOf<String, Any>(
                "userId" to cleanUserId,
                "nombre" to "Invitado",
                "tipoUsuario" to "INVITADO",
                "estado" to "ACTIVO",
                "fechaRegistro" to FieldValue.serverTimestamp(),
                "ultimaActividad" to FieldValue.serverTimestamp(),
                "schemaVersion" to 2
            )
            pref.edit().putString("user_id", cleanUserId).apply()
            val targetRef = firestore.collection("usuarios").document(cleanUserId)
            targetRef.set(userData, SetOptions.merge()).addOnSuccessListener {
                performActivityUpdate(targetRef, cleanUserId, pref)
            }
        } else {
            Log.e("REPO", "Documento $cleanUserId no existe y no hay datos locales para restaurar.")
            pref.edit().clear().apply()
            if (context != null && !context.isFinishing) {
                context.runOnUiThread {
                    AlertDialog.Builder(context)
                        .setTitle("Error con la Base de Datos")
                        .setMessage("El registro de usuario ya no existe en la base de datos.\n\nPor favor, ingrese de nuevo o regístrese para obtener un nuevo ID.")
                        .setCancelable(false)
                        .setPositiveButton("Ingresar / Registrarse") { _, _ ->
                            val intent = Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(intent)
                            context.finish()
                        }
                        .show()
                }
            }
        }
    }

    fun migrarUsuariosLegacyAFuenteUnica(callback: (() -> Unit)? = null) {
        if (!isCloudEnabled()) {
            callback?.invoke()
            return
        }

        firestore.collection("usuarios").get().addOnSuccessListener { query ->
            val documents = query.documents
            if (documents.isEmpty()) {
                callback?.invoke()
                return@addOnSuccessListener
            }

            val legacyDocsToClean = mutableListOf<String>()

            for (doc in documents) {
                val id = doc.id
                if (id.startsWith("mock_user_") || id.startsWith("test_user_")) {
                    legacyDocsToClean.add(id)
                }
            }

            if (legacyDocsToClean.isNotEmpty()) {
                for (legacyId in legacyDocsToClean) {
                    firestore.collection("usuarios").document(legacyId).delete()
                }
            }
            callback?.invoke()
        }.addOnFailureListener {
            callback?.invoke()
        }
    }

    fun purgarUsuariosDuplicadosYPrueba(callback: (() -> Unit)? = null) {
        migrarUsuariosLegacyAFuenteUnica(callback)
    }

    fun getUsuariosActivosReal(callback: (List<Map<String, Any>>) -> Unit) {
        if (isCloudEnabled()) {
            firestore.collection("usuarios")
                .get()
                .addOnSuccessListener { query ->
                    val list = ArrayList<Map<String, Any>>()
                    for (doc in query.documents) {
                        val id = doc.id
                        if (id.startsWith("mock_user_") || id.startsWith("test_user_")) continue
                        val data = doc.data ?: continue
                        list.add(data)
                    }
                    callback(list)
                }
                .addOnFailureListener {
                    Thread {
                        val localUsers = localDb.userDao().getAllUsers()
                        val list = localUsers.map { u ->
                            mapOf(
                                "nombre" to (u.name ?: "Usuario"),
                                "telefono" to (u.phone ?: ""),
                                "dpi" to (u.dpi ?: "N/A"),
                                "modeloDispositivo" to Build.MODEL,
                                "tipoUsuario" to u.role
                            )
                        }
                        Handler(Looper.getMainLooper()).post { callback(list) }
                    }.start()
                }
        } else {
            Thread {
                val localUsers = localDb.userDao().getAllUsers()
                val list = localUsers.map { u ->
                    mapOf(
                        "nombre" to (u.name ?: "Usuario"),
                        "telefono" to (u.phone ?: ""),
                        "dpi" to (u.dpi ?: "N/A"),
                        "modeloDispositivo" to Build.MODEL,
                        "tipoUsuario" to u.role
                    )
                }
                Handler(Looper.getMainLooper()).post { callback(list) }
            }.start()
        }
    }

    fun getGlobalConfig(key: String): String {
        val local = localDb.globalConfigDao().getConfig(key)?.value ?: ""
        if (!isCloudEnabled()) return local
        val task = firestore.collection("global_config").document(key).get()
        return await(task)?.getString("value") ?: local
    }

    fun setGlobalConfig(key: String, value: String) {
        localDb.globalConfigDao().setConfig(GlobalConfigEntity(key, value))
        if (isCloudEnabled()) firestore.collection("global_config").document(key).set(hashMapOf("value" to value))
    }

    interface PublishCallback {
        fun onResult(result: PublishResult)
    }

    interface RestoreCallback {
        fun onResult(success: Boolean, message: String)
    }

    fun checkAdminPassword(password: String): Boolean = authManager.checkPassword(password)
    fun updateAdminPassword(newPass: String) = authManager.updatePassword(newPass)

    // --- MODELOS DE RESULTADO PARA PUBLICACIÓN Y STATUS ---
    data class PublishResult(
        val success: Boolean,
        val version: Int = 0,
        val timestamp: Long = System.currentTimeMillis(),
        val sectionsCount: Int = 0,
        val itemsCount: Int = 0,
        val errorMessage: String? = null
    )

    data class SyncStatusInfo(
        val localVersion: Int,
        val remoteVersion: Int,
        val lastSyncTimestamp: Long,
        val hasUnpublishedChanges: Boolean,
        val sectionsCount: Int,
        val itemsCount: Int,
        val isCloudSyncActive: Boolean,
        val errorMessage: String? = null
    )

    // --- PUBLICACIÓN ATÓMICA DE CONFIGURACIÓN ---
    fun publishCurrentConfiguration(callback: PublishCallback) {
        Thread {
            try {
                val sections = localDb.sectionDao().getAllSections()
                val items = localDb.contentDao().getAllItems()
                val blocks = localDb.contentDao().getAllBlocks()
                val navigation = localDb.navigationDao().getAllItems()
                val configs = localDb.globalConfigDao().getAllConfigs()

                // 1. Validaciones previas obligatorias
                if (sections.isEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        callback.onResult(PublishResult(false, errorMessage = "No hay pantallas para publicar."))
                    }
                    return@Thread
                }

                val emptyTitles = sections.filter { it.title.trim().isEmpty() || it.slug.trim().isEmpty() }
                if (emptyTitles.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        callback.onResult(PublishResult(false, errorMessage = "Existen pantallas con título o ruta vacía."))
                    }
                    return@Thread
                }

                val duplicateSlugs = sections.groupBy { it.slug }.filter { it.value.size > 1 }
                if (duplicateSlugs.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        callback.onResult(PublishResult(false, errorMessage = "Existen rutas/IDs de pantallas duplicadas: ${duplicateSlugs.keys.joinToString()}"))
                    }
                    return@Thread
                }

                val pref = context.getSharedPreferences("ConfigSyncPrefs", Context.MODE_PRIVATE)
                val currentLocalVersion = pref.getInt("published_version", 1)

                // 2. Obtener versión remota actual
                val task = firestore.collection("config").document("published_config").get()
                val snapshot = await(task)
                val remoteVersion = snapshot?.getLong("version")?.toInt() ?: 0

                val newVersion = Math.max(currentLocalVersion, remoteVersion) + 1
                val timestamp = System.currentTimeMillis()

                // 3. Marcar localmente como publicado
                sections.forEach {
                    it.isPublished = true
                    it.version = newVersion
                    it.updatedAt = timestamp
                    localDb.sectionDao().insert(it)
                }
                items.forEach {
                    it.isDraft = false
                    it.updatedAt = timestamp
                    localDb.contentDao().insertItem(it)
                }
                blocks.forEach {
                    it.isDraft = false
                    it.updatedAt = timestamp
                    localDb.contentDao().insertBlock(it)
                }

                // 4. Construir payload completo para Firestore
                val payload = hashMapOf(
                    "version" to newVersion,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to "Admin_Device_${Build.MODEL}",
                    "sectionsCount" to sections.size,
                    "itemsCount" to items.size,
                    "blocksCount" to blocks.size,
                    "isPublished" to true
                )

                // Guardar documento maestro de versión
                firestore.collection("config").document("published_config").set(payload)
                
                // Publicar cada entidad a Firestore
                sections.forEach { firestore.collection("sections").document(it.id).set(it) }
                items.forEach { firestore.collection("content_items").document(it.id).set(it) }
                blocks.forEach { firestore.collection("content_blocks").document(it.id).set(it) }
                navigation.forEach { firestore.collection("navigation_items").document(it.id).set(it) }
                configs.forEach { firestore.collection("global_config").document(it.key).set(hashMapOf("value" to it.value)) }

                Log.d("COLUA_CMS", "Published version $newVersion with ${sections.size} sections, ${items.size} items, ${blocks.size} blocks")

                // 5. Guardar versión e info localmente
                pref.edit()
                    .putInt("published_version", newVersion)
                    .putLong("last_sync_timestamp", timestamp)
                    .putBoolean("has_unpublished_changes", false)
                    .apply()

                authManager.isCloudSyncEnabled = true

                Handler(Looper.getMainLooper()).post {
                    callback.onResult(PublishResult(
                        success = true,
                        version = newVersion,
                        timestamp = timestamp,
                        sectionsCount = sections.size,
                        itemsCount = items.size
                    ))
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    callback.onResult(PublishResult(false, errorMessage = "Error al publicar: ${e.message}"))
                }
            }
        }.start()
    }

    // --- ESTADO DE SINCRONIZACIÓN Y BORRADOR ---
    fun getSyncStatusInfo(): SyncStatusInfo {
        val pref = context.getSharedPreferences("ConfigSyncPrefs", Context.MODE_PRIVATE)
        val localVersion = pref.getInt("published_version", 1)
        val lastSyncTimestamp = pref.getLong("last_sync_timestamp", 0L)
        val hasUnpublished = pref.getBoolean("has_unpublished_changes", false)

        val sectionsCount = localDb.sectionDao().getAllSections().size
        val itemsCount = localDb.contentDao().getAllItems().size

        var remoteVersion = localVersion
        var errorMsg: String? = null

        if (isCloudEnabled()) {
            val task = firestore.collection("config").document("published_config").get()
            val snapshot = await(task)
            if (snapshot != null && snapshot.exists()) {
                remoteVersion = snapshot.getLong("version")?.toInt() ?: localVersion
            } else if (snapshot == null) {
                errorMsg = "Sin conexión con servidor Firestore"
            }
        }

        return SyncStatusInfo(
            localVersion = localVersion,
            remoteVersion = remoteVersion,
            lastSyncTimestamp = lastSyncTimestamp,
            hasUnpublishedChanges = hasUnpublished,
            sectionsCount = sectionsCount,
            itemsCount = itemsCount,
            isCloudSyncActive = isCloudEnabled(),
            errorMessage = errorMsg
        )
    }

    // --- RESTAURAR DATOS INICIALES SEGURO (CON RESPALDO) ---
    fun restoreInitialDataWithBackup(callback: RestoreCallback) {
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                
                // 1. Crear copia de seguridad antes de restaurar
                val currentSections = localDb.sectionDao().getAllSections()
                val currentItems = localDb.contentDao().getAllItems()
                val backupData = hashMapOf(
                    "backupTimestamp" to timestamp,
                    "sectionsCount" to currentSections.size,
                    "itemsCount" to currentItems.size,
                    "sections" to currentSections,
                    "items" to currentItems
                )
                
                if (isCloudEnabled()) {
                    firestore.collection("config").document("backup_before_restore").set(backupData)
                }

                // 2. Restaurar contenido inicial (NO TOCA USUARIOS, TELÉFONOS, DPIS NI LOGS)
                DataSeeder.seedIfEmpty(context, true)

                // 3. Marcar el contenido restaurado como BORRADOR (no publicado aún)
                val restoredSections = localDb.sectionDao().getAllSections()
                restoredSections.forEach {
                    it.isPublished = false
                    localDb.sectionDao().insert(it)
                }

                val pref = context.getSharedPreferences("ConfigSyncPrefs", Context.MODE_PRIVATE)
                pref.edit().putBoolean("has_unpublished_changes", true).apply()

                Handler(Looper.getMainLooper()).post {
                    callback.onResult(true, "Datos iniciales restaurados en Modo Borrador. Puedes revisar la vista previa antes de publicar.")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    callback.onResult(false, "Error al restaurar: ${e.message}")
                }
            }
        }.start()
    }

    // --- ESCUCHADOR EN TIEMPO REAL PARA APLICACIÓN CLIENTE ---
    fun subscribeToPublishedConfig(onConfigUpdated: (Int) -> Unit) {
        firestore.collection("config").document("published_config")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                
                val remoteVersion = snapshot.getLong("version")?.toInt() ?: return@addSnapshotListener
                val pref = context.getSharedPreferences("ConfigSyncPrefs", Context.MODE_PRIVATE)
                val localVersion = pref.getInt("published_version", 1)

                if (remoteVersion > localVersion) {
                    // Descargar y actualizar contenido publicado de forma segura
                    Thread {
                        try {
                            val secTask = firestore.collection("sections").get()
                            val secDocs = await(secTask)?.toObjects(SectionEntity::class.java)
                            secDocs?.forEach { 
                                try {
                                    localDb.sectionDao().insert(it)
                                } catch (e: Exception) {
                                    Log.e("REPO", "Error inserting section: ${e.message}")
                                }
                            }

                            val itemTask = firestore.collection("content_items").get()
                            val itemDocs = await(itemTask)?.toObjects(ContentItemEntity::class.java)
                            itemDocs?.forEach { 
                                try {
                                    val secId = it.sectionId
                                    if (!secId.isEmpty()) {
                                        val sectionExists = localDb.sectionDao().getAllSections().any { s -> s.id.equals(secId, ignoreCase = true) }
                                        if (sectionExists) {
                                            localDb.contentDao().insertItem(it)
                                        } else {
                                            Log.w("REPO", "Skipping item ${it.id}: section $secId does not exist locally.")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("REPO", "Error inserting item: ${e.message}")
                                }
                            }

                            val navTask = firestore.collection("navigation_items").get()
                            val navDocs = await(navTask)?.toObjects(NavigationItemEntity::class.java)
                            navDocs?.forEach { 
                                try {
                                    localDb.navigationDao().insert(it)
                                } catch (e: Exception) {
                                    Log.e("REPO", "Error inserting nav: ${e.message}")
                                }
                            }

                            pref.edit().putInt("published_version", remoteVersion).apply()

                            Handler(Looper.getMainLooper()).post {
                                onConfigUpdated(remoteVersion)
                            }
                        } catch (e: Exception) {
                            Log.e("REPO", "Error syncing published config: ${e.message}")
                        }
                    }.start()
                }
            }
    }

    fun migrateLocalDataToCloud() {
        publishCurrentConfiguration(object : PublishCallback {
            override fun onResult(result: PublishResult) {
                if (result.success) {
                    Toast.makeText(context, "Migración exitosa (v${result.version})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    fun purgeCloudAgencias() {
        firestore.collection("agencias").get().addOnSuccessListener { docs -> docs.forEach { it.reference.delete() } }
    }

    fun purgeOldCollections() {
        // 1. Borrar colección obsoleta 'associates'
        firestore.collection("associates").get().addOnSuccessListener { docs ->
            docs.forEach { it.reference.delete() }
        }

        // 2. Limpiar únicamente registros de prueba mock_user_ o test_user_ en 'usuarios'
        firestore.collection("usuarios").get().addOnSuccessListener { docs ->
            docs.forEach { doc ->
                val id = doc.id
                if (id.startsWith("mock_user_") || id.startsWith("test_user_")) {
                    doc.reference.delete()
                }
            }
        }
    }

    /**
     * Script Administrativo Seguro: Limpieza Profunda de Datos de Usuario.
     * Elimina perfiles, subcolecciones, sesiones y contadores, asegurando
     * que la base de datos de usuarios quede completamente en cero (0).
     */
    fun deepWipeAllUserData(callback: (String) -> Unit) {
        val targetCollections = listOf(
            "usuarios", "users", "profiles", "perfiles", 
            "admins", "sessions", "tokens", "devices", "roles", 
            "solicitudes", "favoritos", "historial", "associates"
        )
        
        val report = StringBuilder("Iniciando Deep Wipe Exclusivo de Usuarios...\n")
        var pendingCollections = targetCollections.size
        var totalDeleted = 0

        targetCollections.forEach { collName ->
            firestore.collection(collName).get().addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    report.append("- Colección '$collName': ${query.size()} documentos encontrados.\n")
                    query.documents.forEach { doc ->
                        // Si es 'usuarios', primero intentar limpiar subcolecciones conocidas
                        if (collName == "usuarios" || collName == "users") {
                            val subcollections = listOf("dispositivos", "sessions", "tokens")
                            subcollections.forEach { subColl ->
                                doc.reference.collection(subColl).get().addOnSuccessListener { subQuery ->
                                    subQuery.documents.forEach { it.reference.delete() }
                                }
                            }
                        }
                        doc.reference.delete()
                        totalDeleted++
                    }
                }
                pendingCollections--
                checkWipeCompletion(pendingCollections, totalDeleted, report, callback)
            }.addOnFailureListener {
                pendingCollections--
                checkWipeCompletion(pendingCollections, totalDeleted, report, callback)
            }
        }
    }

    private fun checkWipeCompletion(pending: Int, totalDeleted: Int, report: StringBuilder, callback: (String) -> Unit) {
        if (pending == 0) {
            // Limpiar contadores
            firestore.collection("systemCounters").document("usuarios").delete()
            firestore.collection("systemCounters").document("invitados").delete()
            
            report.append("\nContadores de secuencia de usuarios reseteados.\n")
            report.append("Total de documentos de usuario eliminados: $totalDeleted\n")
            report.append("El CMS (Noticias, Secciones) NO fue alterado.\n")
            
            Log.i("DEEP_WIPE", report.toString())
            callback(report.toString())
        }
    }
}

