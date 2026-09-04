package com.example.coluainformativa.repository

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.coluainformativa.database.*
import com.example.coluainformativa.security.AdminAuthManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.FieldValue
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
        FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                callback(task.result ?: UUID.randomUUID().toString())
            } else {
                callback(UUID.randomUUID().toString())
            }
        }
    }

    private fun <T> await(task: com.google.android.gms.tasks.Task<T>): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        return try { Tasks.await(task, 15, TimeUnit.SECONDS) } catch (e: Exception) { null }
    }

    private fun isCloudEnabled(): Boolean = authManager.isCloudSyncEnabled

    // --- SECCIONES (ROBUSTO) ---
    fun getAllSections(): List<SectionEntity> {
        cleanupOrphanNavigationItems()
        val local = localDb.sectionDao().getAllSections()
        if (!isCloudEnabled()) return local
        
        try {
            val task = firestore.collection("sections").orderBy("displayOrder").get()
            val cloud = await(task)?.toObjects(SectionEntity::class.java)
            if (cloud != null && cloud.isNotEmpty()) {
                val map = linkedMapOf<String, SectionEntity>()
                for (s in local) { map[s.id] = s }
                for (s in cloud) { map[s.id] = s }
                return map.values.sortedBy { it.displayOrder }
            }
        } catch (e: Exception) {
            // Fallback to local
        }
        return local
    }

    fun getVisibleSections(): List<SectionEntity> {
        val local = localDb.sectionDao().getVisibleSections()
        if (!isCloudEnabled()) return local

        try {
            val task = firestore.collection("sections").whereEqualTo("isVisible", true).orderBy("displayOrder").get()
            val cloud = await(task)?.toObjects(SectionEntity::class.java)
            if (cloud != null && cloud.isNotEmpty()) {
                val map = linkedMapOf<String, SectionEntity>()
                for (s in local) { map[s.id] = s }
                for (s in cloud) { map[s.id] = s }
                return map.values.filter { it.isVisible }.sortedBy { it.displayOrder }
            }
        } catch (e: Exception) {
            // Fallback to local
        }
        return local
    }

    fun insertSection(section: SectionEntity) {
        if (section.id.isEmpty()) section.id = UUID.randomUUID().toString()
        localDb.sectionDao().insert(section)
        if (isCloudEnabled()) firestore.collection("sections").document(section.id).set(section)
    }

    fun deleteSection(id: String) {
        localDb.sectionDao().deleteById(id)
        localDb.navigationDao().deleteByTargetSection(id)
        localDb.navigationDao().deleteById("nav_$id")
        localDb.contentDao().deleteItemsBySection(id)
        localDb.contentDao().deleteBlocksBySection(id)
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
        val resolvedId = resolveSectionId(sectionId)
        val local = localDb.contentDao().getItemsBySection(resolvedId)
        val fallbackLocal = if (local.isEmpty() && resolvedId != sectionId) localDb.contentDao().getItemsBySection(sectionId) else local
        
        if (!isCloudEnabled()) return if (local.isNotEmpty()) local else fallbackLocal

        val task = firestore.collection("content_items").whereEqualTo("sectionId", resolvedId).orderBy("displayOrder").get()
        val docs = await(task)?.documents ?: return if (local.isNotEmpty()) local else fallbackLocal
        val list = docs.mapNotNull { it.toObject(ContentItemEntity::class.java) }
        return if (list.isNotEmpty()) list else (if (local.isNotEmpty()) local else fallbackLocal)
    }

    fun getPublishedItemsBySection(sectionId: String): List<ContentItemEntity> {
        val resolvedId = resolveSectionId(sectionId)
        val local = localDb.contentDao().getPublishedItemsBySection(resolvedId, System.currentTimeMillis())
        val fallbackLocal = if (local.isEmpty() && resolvedId != sectionId) localDb.contentDao().getPublishedItemsBySection(sectionId, System.currentTimeMillis()) else local

        if (!isCloudEnabled()) return if (local.isNotEmpty()) local else fallbackLocal

        val task = firestore.collection("content_items").whereEqualTo("sectionId", resolvedId).whereEqualTo("isDraft", false).get()
        val docs = await(task)?.documents ?: return if (local.isNotEmpty()) local else fallbackLocal
        val list = docs.mapNotNull { it.toObject(ContentItemEntity::class.java) }
        val now = System.currentTimeMillis()
        val filtered = list.filter { it.isVisible && it.publicationDate <= now }.sortedBy { it.displayOrder }
        return if (filtered.isNotEmpty()) filtered else (if (local.isNotEmpty()) local else fallbackLocal)
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

    // --- BLOQUES ---
    fun getBlocksBySection(sectionId: String): List<ContentBlockEntity> {
        val local = localDb.contentDao().getBlocksBySection(sectionId)
        if (!isCloudEnabled()) return local
        val task = firestore.collection("content_blocks").whereEqualTo("sectionId", sectionId).orderBy("displayOrder").get()
        val cloud = await(task)?.toObjects(ContentBlockEntity::class.java)
        return if (cloud != null && cloud.isNotEmpty()) cloud else local
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

    // --- AGENCIAS ---
    fun getAllAgencias(): List<AgenciaEntity> {
        val local = localDb.agenciaDao().getAllAgencias()
        if (!isCloudEnabled()) return local
        val task = firestore.collection("agencias").get()
        val docs = await(task)?.documents ?: return local
        val list = docs.mapNotNull { doc ->
            doc.toObject(AgenciaEntity::class.java)?.apply { if (id.isEmpty()) id = doc.id }
        }
        return if (list.isNotEmpty()) list.sortedBy { it.departamento } else local
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

    fun deleteAgencia(agencia: AgenciaEntity) {
        localDb.agenciaDao().delete(agencia)
        if (isCloudEnabled()) firestore.collection("agencias").document(agencia.id).delete()
    }

    // --- NAVEGACIÓN (Navbar, Sidebar, BottomNav) ---
    fun getVisibleNavigation(type: String): List<NavigationItemEntity> {
        val local = localDb.navigationDao().getVisibleItemsByType(type)
        if (!isCloudEnabled()) return local
        
        val task = firestore.collection("navigation_items")
            .whereEqualTo("type", type)
            .whereEqualTo("isVisible", true)
            .orderBy("displayOrder").get()
        
        val cloud = await(task)?.toObjects(NavigationItemEntity::class.java)
        // SI LA NUBE ESTÁ VACÍA, USAMOS LOCAL. SIEMPRE.
        return if (cloud != null && cloud.isNotEmpty()) cloud else local
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

    private fun getNextUserId(callback: (String) -> Unit, onError: (Exception) -> Unit = {}) {
        val counterRef = firestore.collection("systemCounters").document("users")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            var nextSeq = 0L
            if (snapshot.exists()) {
                nextSeq = snapshot.getLong("nextSeq") ?: 0L
            } else {
                nextSeq = 0L
            }
            val newSeq = nextSeq + 1L
            transaction.set(counterRef, hashMapOf("nextSeq" to newSeq), SetOptions.merge())
            String.format(Locale.getDefault(), "user%08d", nextSeq)
        }.addOnSuccessListener { userId ->
            Log.i("FIRESTORE_COUNTER", "Generated next userId: $userId")
            callback(userId)
        }.addOnFailureListener { e ->
            Log.e("FIRESTORE_COUNTER", "Error getting next user id: ${e.message}", e)
            onError(e)
        }
    }

    @JvmOverloads
    fun registrarUsuarioReal(nombre: String, telefono: String, rawDpi: String, esInvitado: Boolean, callback: (Boolean, String, String?) -> Unit = { _, _, _ -> }) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener { _ ->
                proceedWithRegistration(nombre, telefono, rawDpi, esInvitado, callback)
            }
        } else {
            proceedWithRegistration(nombre, telefono, rawDpi, esInvitado, callback)
        }
    }

    private fun proceedWithRegistration(nombre: String, telefono: String, rawDpi: String, esInvitado: Boolean, callback: (Boolean, String, String?) -> Unit) {
        getInstallationId { installId ->
            val normalizedDpi = if (esInvitado || rawDpi.isEmpty()) "guest_${installId}" else normalizeDpi(rawDpi)
            Log.i("FIRESTORE_REG", "Iniciando registro. Nombre: $nombre, DPI: $rawDpi, InstallId: $installId")

            try {
                purgarUsuariosDuplicados()

                val query = if (!esInvitado && !rawDpi.isEmpty()) {
                    firestore.collection("usuarios").whereEqualTo("dpiNormalizado", normalizedDpi).get()
                } else {
                    null
                }

                if (query != null) {
                    query.addOnSuccessListener { querySnapshot ->
                        if (querySnapshot != null && !querySnapshot.isEmpty) {
                            val existingDoc = querySnapshot.documents[0]
                            val existingUserId = existingDoc.getString("userId") ?: existingDoc.id
                            Log.i("FIRESTORE_REG", "Usuario existente encontrado por DPI. Reutilizando userId: $existingUserId")
                            updateExistingUser(existingUserId, nombre, telefono, rawDpi, normalizedDpi, installId, callback)
                        } else {
                            checkOrCreatePrimaryUser(nombre, telefono, rawDpi, normalizedDpi, esInvitado, installId, callback)
                        }
                    }.addOnFailureListener {
                        checkOrCreatePrimaryUser(nombre, telefono, rawDpi, normalizedDpi, esInvitado, installId, callback)
                    }
                } else {
                    checkOrCreatePrimaryUser(nombre, telefono, rawDpi, normalizedDpi, esInvitado, installId, callback)
                }
            } catch (e: Exception) {
                createNewUser(nombre, telefono, rawDpi, normalizedDpi, esInvitado, installId, callback)
            }
        }
    }

    private fun updateExistingUser(userId: String, nombre: String, telefono: String, rawDpi: String, normalizedDpi: String, installId: String, callback: (Boolean, String, String?) -> Unit) {
        firestore.collection("usuarios").document(userId).get().addOnSuccessListener { doc ->
            val existingDpi = doc.getString("dpi") ?: ""
            val finalDpi = if (rawDpi.isNotEmpty() && !rawDpi.startsWith("user") && !rawDpi.equals(userId)) rawDpi else existingDpi
            val finalNormalizedDpi = if (finalDpi.isNotEmpty()) normalizeDpi(finalDpi) else (doc.getString("dpiNormalizado") ?: normalizedDpi)

            val updateData = linkedMapOf<String, Any>(
                "userId" to userId,
                "nombre" to nombre,
                "telefono" to telefono,
                "dpi" to finalDpi,
                "dpiNormalizado" to finalNormalizedDpi,
                "ultimaActividad" to FieldValue.serverTimestamp()
            )
            firestore.collection("usuarios").document(userId).set(updateData, SetOptions.merge())

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

            saveLocalUser(userId, finalDpi, nombre, telefono, "MEMBER")
            callback(true, userId, null)
        }.addOnFailureListener {
            val updateData = linkedMapOf<String, Any>(
                "userId" to userId,
                "nombre" to nombre,
                "telefono" to telefono,
                "dpi" to rawDpi,
                "dpiNormalizado" to normalizedDpi,
                "ultimaActividad" to FieldValue.serverTimestamp()
            )
            firestore.collection("usuarios").document(userId).set(updateData, SetOptions.merge())
            saveLocalUser(userId, rawDpi, nombre, telefono, "MEMBER")
            callback(true, userId, null)
        }
    }

    private fun checkOrCreatePrimaryUser(nombre: String, telefono: String, rawDpi: String, normalizedDpi: String, esInvitado: Boolean, installId: String, callback: (Boolean, String, String?) -> Unit) {
        firestore.collection("usuarios").orderBy("fechaRegistro").limit(1).get().addOnSuccessListener { querySnapshot ->
            if (querySnapshot != null && !querySnapshot.isEmpty) {
                val existingDoc = querySnapshot.documents[0]
                val existingUserId = existingDoc.getString("userId") ?: existingDoc.id
                updateExistingUser(existingUserId, nombre, telefono, rawDpi, normalizedDpi, installId, callback)
            } else {
                createNewUser(nombre, telefono, rawDpi, normalizedDpi, esInvitado, installId, callback)
            }
        }.addOnFailureListener {
            createNewUser(nombre, telefono, rawDpi, normalizedDpi, esInvitado, installId, callback)
        }
    }

    fun purgarUsuariosDuplicados() {
        if (!isCloudEnabled()) return
        try {
            firestore.collection("usuarios").get().addOnSuccessListener { query ->
                val docs = query.documents.sortedBy { it.getString("fechaRegistro").toString() }
                if (docs.size > 1) {
                    val primaryDoc = docs[0]
                    val primaryId = primaryDoc.id
                    for (i in 1 until docs.size) {
                        val duplicateDoc = docs[i]
                        val duplicateId = duplicateDoc.id
                        if (duplicateId != primaryId) {
                            Log.i("REPO", "Purgando usuario duplicado: $duplicateId")
                            firestore.collection("usuarios").document(duplicateId).delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Error purgando duplicados: ${e.message}")
        }
    }

    private fun createNewUser(nombre: String, telefono: String, rawDpi: String, normalizedDpi: String, esInvitado: Boolean, installId: String, callback: (Boolean, String, String?) -> Unit) {
        getNextUserId({ userId ->
            val userData = linkedMapOf<String, Any>(
                "userId" to userId,
                "nombre" to nombre,
                "telefono" to telefono,
                "dpi" to rawDpi,
                "dpiNormalizado" to normalizedDpi,
                "tipoUsuario" to (if (esInvitado) "INVITADO" else "ASOCIADO"),
                "estado" to "ACTIVO",
                "fechaRegistro" to FieldValue.serverTimestamp(),
                "ultimaActividad" to FieldValue.serverTimestamp()
            )

            saveLocalUser(userId, rawDpi, nombre, telefono, if (esInvitado) "GUEST" else "MEMBER")

            try {
                firestore.collection("usuarios").document(userId).set(userData, SetOptions.merge())
                    .addOnSuccessListener {
                        val deviceData = hashMapOf<String, Any>(
                            "installationId" to installId,
                            "modeloDispositivo" to Build.MODEL,
                            "fabricante" to Build.MANUFACTURER,
                            "primeraActividad" to FieldValue.serverTimestamp(),
                            "ultimaActividad" to FieldValue.serverTimestamp(),
                            "activo" to true
                        )
                        firestore.collection("usuarios").document(userId)
                            .collection("dispositivos").document(installId)
                            .set(deviceData, SetOptions.merge())
                    }
            } catch (e: Exception) {
                Log.e("FIRESTORE_REG", "Error sync nube: ${e.message}")
            }

            callback(true, userId, null)
        }, { e ->
            val fallbackUserId = "user0000000"
            saveLocalUser(fallbackUserId, rawDpi, nombre, telefono, if (esInvitado) "GUEST" else "MEMBER")
            callback(true, fallbackUserId, null)
        })
    }

    private fun saveLocalUser(userId: String, dpi: String, name: String, phone: String, role: String) {
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
    }

    fun actualizarUltimaActividad() {
        val pref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userId = pref.getString("user_id", "") ?: ""
        val name = pref.getString("user_name", "Invitado") ?: "Invitado"
        val phone = pref.getString("user_phone", "") ?: ""

        if (userId.isNotEmpty() && userId.matches(Regex("user\\d{7}"))) {
            getInstallationId { installId ->
                val userUpdate = hashMapOf<String, Any>(
                    "nombre" to name,
                    "telefono" to phone,
                    "ultimaActividad" to FieldValue.serverTimestamp(),
                    "estado" to "ACTIVO"
                )
                firestore.collection("usuarios").document(userId).set(userUpdate, SetOptions.merge())

                val deviceUpdate = hashMapOf<String, Any>(
                    "installationId" to installId,
                    "modeloDispositivo" to Build.MODEL,
                    "fabricante" to Build.MANUFACTURER,
                    "ultimaActividad" to FieldValue.serverTimestamp(),
                    "activo" to true
                )
                firestore.collection("usuarios").document(userId)
                    .collection("dispositivos").document(installId)
                    .set(deviceUpdate, SetOptions.merge())
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

            val dpiMap = HashMap<String, MutableList<DocumentSnapshot>>()
            val legacyDocsToClean = mutableListOf<String>()

            for (doc in documents) {
                val id = doc.id
                if (id.startsWith("mock_user_")) {
                    legacyDocsToClean.add(id)
                    continue
                }

                if (id.matches(Regex("user\\d{7}"))) {
                    continue
                }

                legacyDocsToClean.add(id)
                val data = doc.data
                val dpi = data?.get("dpi")?.toString() ?: id
                val normalized = normalizeDpi(dpi)
                if (normalized.isNotEmpty()) {
                    val list = dpiMap.getOrPut(normalized) { mutableListOf() }
                    list.add(doc)
                }
            }

            processNextMigrationGroup(dpiMap.entries.iterator(), legacyDocsToClean, callback)
        }.addOnFailureListener {
            callback?.invoke()
        }
    }

    private fun processNextMigrationGroup(
        iterator: MutableIterator<MutableMap.MutableEntry<String, MutableList<DocumentSnapshot>>>,
        legacyDocsToClean: MutableList<String>,
        callback: (() -> Unit)?
    ) {
        if (!iterator.hasNext()) {
            for (legacyId in legacyDocsToClean) {
                firestore.collection("usuarios").document(legacyId).delete()
            }
            callback?.invoke()
            return
        }

        val entry = iterator.next()
        val docs = entry.value
        val sampleData = docs[0].data ?: emptyMap<String, Any>()
        val nombre = sampleData["nombre"]?.toString() ?: "Usuario"
        val telefono = sampleData["telefono"]?.toString() ?: ""
        val dpi = sampleData["dpi"]?.toString() ?: ""
        val normalizedDpi = entry.key

        getNextUserId({ userId ->
            val userData = hashMapOf<String, Any>(
                "userId" to userId,
                "nombre" to nombre,
                "telefono" to telefono,
                "dpi" to dpi,
                "dpiNormalizado" to normalizedDpi,
                "tipoUsuario" to (sampleData["tipoUsuario"] ?: "ASOCIADO"),
                "estado" to "ACTIVO",
                "fechaRegistro" to (sampleData["fechaRegistro"] ?: FieldValue.serverTimestamp()),
                "ultimaActividad" to FieldValue.serverTimestamp()
            )

            firestore.collection("usuarios").document(userId).set(userData).addOnSuccessListener {
                for (doc in docs) {
                    val data = doc.data ?: continue
                    val installId = data["installationId"]?.toString() ?: doc.id
                    val deviceData = hashMapOf<String, Any>(
                        "installationId" to installId,
                        "modeloDispositivo" to (data["modeloDispositivo"]?.toString() ?: Build.MODEL),
                        "fabricante" to (data["fabricanteDispositivo"]?.toString() ?: Build.MANUFACTURER),
                        "primeraActividad" to (data["fechaRegistro"] ?: FieldValue.serverTimestamp()),
                        "ultimaActividad" to (data["ultimaActividad"] ?: FieldValue.serverTimestamp()),
                        "activo" to true
                    )
                    firestore.collection("usuarios").document(userId)
                        .collection("dispositivos").document(installId)
                        .set(deviceData, SetOptions.merge())
                }
                processNextMigrationGroup(iterator, legacyDocsToClean, callback)
            }.addOnFailureListener {
                processNextMigrationGroup(iterator, legacyDocsToClean, callback)
            }
        }, { e ->
            processNextMigrationGroup(iterator, legacyDocsToClean, callback)
        })
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
                        if (id.startsWith("mock_user_") || !id.matches(Regex("user\\d{7}"))) continue
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

                // 4. Construir payload completo para Firestore
                val payload = hashMapOf(
                    "version" to newVersion,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to "Admin_Device_${Build.MODEL}",
                    "sectionsCount" to sections.size,
                    "itemsCount" to items.size,
                    "isPublished" to true
                )

                // Guardar documento maestro de versión
                firestore.collection("config").document("published_config").set(payload)
                
                // Publicar cada entidad a Firestore
                sections.forEach { firestore.collection("sections").document(it.id).set(it) }
                items.forEach { firestore.collection("content_items").document(it.id).set(it) }
                navigation.forEach { firestore.collection("navigation_items").document(it.id).set(it) }
                configs.forEach { firestore.collection("global_config").document(it.key).set(hashMapOf("value" to it.value)) }

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

        // 2. Limpiar documentos fantasma y registros antiguos en 'usuarios'
        firestore.collection("usuarios").get().addOnSuccessListener { docs ->
            docs.forEach { doc ->
                val id = doc.id
                // Borrar si es un ID de instalación antiguo de la versión previa (sin guion bajo) o si es un usuario mock
                if (!id.contains("_") || id.startsWith("mock_user_")) {
                    doc.reference.delete()
                }
            }
        }
    }
}

