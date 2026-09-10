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

    fun getItemsBySection(sectionId: String): List<ContentItemEntity> {
        val cleanId = sectionId.lowercase(Locale.getDefault())
        val altId = if (cleanId.startsWith("sec_")) cleanId.replace("sec_", "") else "sec_$cleanId"

        val local = localDb.contentDao().getItemsBySection(cleanId).toMutableList()
        val altLocal = localDb.contentDao().getItemsBySection(altId)

        for (item in altLocal) {
            if (local.none { it.id == item.id }) {
                local.add(item)
            }
        }

        for (item in local) {
            if (item.likesCount == 184 || item.likesCount == 183 || item.likesCount == 96 || item.likesCount == 112) {
                item.likesCount = 0
                item.sharesCount = 0
                localDb.contentDao().insertItem(item)
            }
        }

        if (local.isNotEmpty()) return local.sortedBy { it.displayOrder }

        if (!isCloudEnabled()) return local
        val task = firestore.collection("content_items").whereIn("sectionId", listOf(cleanId, altId)).get()
        val cloud = await(task)?.toObjects(ContentItemEntity::class.java)
        if (cloud != null && cloud.isNotEmpty()) {
            cloud.forEach { localDb.contentDao().insertItem(it) }
            return cloud.sortedBy { it.displayOrder }
        }
        return local
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
    fun getBlocksBySection(sectionId: String): List<ContentBlockEntity> {
        val cleanId = sectionId.lowercase(Locale.getDefault())
        val altId = if (cleanId.startsWith("sec_")) cleanId.replace("sec_", "") else "sec_$cleanId"

        val local = localDb.contentDao().getBlocksBySection(cleanId).toMutableList()
        val altLocal = localDb.contentDao().getBlocksBySection(altId)

        for (b in altLocal) {
            if (local.none { it.id == b.id }) {
                local.add(b)
            }
        }

        if (local.isNotEmpty()) return local.sortedBy { it.displayOrder }

        if (!isCloudEnabled()) return local
        val task = firestore.collection("content_blocks").whereIn("sectionId", listOf(cleanId, altId)).get()
        val cloud = await(task)?.toObjects(ContentBlockEntity::class.java)
        if (cloud != null && cloud.isNotEmpty()) {
            cloud.forEach { localDb.contentDao().insertBlock(it) }
            return cloud.sortedBy { it.displayOrder }
        }
        return local
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

        saveLocalUser(targetUserId, formattedDpi, nombre, cleanPhone, pref.getString("user_role", "MEMBER") ?: "MEMBER")

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
                        saveLocalUser(guestId, "", "Invitado", "", "GUEST")
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
                        saveLocalUser(fallbackGuest, "", "Invitado", "", "GUEST")
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
                        saveLocalUser(fallbackId, formattedDpi, nombre, cleanPhone, "MEMBER")
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
                            saveLocalUser(fallbackId, formattedDpi, nombre, cleanPhone, "MEMBER")
                            callback(true, fallbackId, null)
                        }
                }
            } catch (e: Exception) {
                Log.w("FIRESTORE_REG", "Excepción en registro: ${e.message}")
                val fallbackId = if (esInvitado) "guest_local" else "user0000000"
                saveLocalUser(fallbackId, rawDpi, nombre, telefono, if (esInvitado) "GUEST" else "MEMBER")
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

    private fun saveLocalUser(userId: String, dpi: String, name: String, phone: String, role: String) {
        val pref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val cleanUserId = userId.removePrefix("user")
        pref.edit()
            .putString("user_id", cleanUserId)
            .putString("user_dpi", dpi)
            .putString("user_name", name)
            .putString("user_phone", phone)
            .putString("user_role", role)
            .apply()

        Thread {
            val localUser = UserEntity(cleanUserId, dpi, name, phone, role)
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
}

