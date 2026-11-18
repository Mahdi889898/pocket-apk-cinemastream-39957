package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.LibraryItem
import com.example.data.model.MediaSource
import com.example.data.model.MediaType
import com.example.data.model.SourceType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class CinemaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("کاربر باید با حساب گوگل وارد شده باشد.")

    fun observeSources(userId: String): Flow<List<MediaSource>> = flow {
        require(userId.isNotBlank()) { "شناسه کاربر نامعتبر است." }
        val path = "users/$userId/sources"
        emitAll(
            db.collection("users").document(userId).collection("sources")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MediaSource::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeLibrary(userId: String): Flow<List<LibraryItem>> = flow {
        require(userId.isNotBlank()) { "شناسه کاربر نامعتبر است." }
        val path = "users/$userId/library"
        emitAll(
            db.collection("users").document(userId).collection("library")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LibraryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addSource(
        userId: String,
        title: String,
        url: String,
        sourceType: SourceType,
        category: String,
        logoUrl: String? = null,
        channelCount: Long = 0L,
    ): Result<String> {
        var docPath = "users/$userId/sources"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "حساب کاربری تغییر کرده است." }

            val cleanTitle = title.trim().take(120)
            val cleanUrl = url.trim().take(1200)
            val cleanCategory = category.trim().ifBlank { "عمومی" }.take(60)
            val cleanLogo = logoUrl?.trim()?.takeIf { it.isNotBlank() }?.take(1200)
            val safeChannelCount = channelCount.coerceIn(0L, 50000L)

            require(cleanTitle.isNotEmpty()) { "عنوان منبع نباید خالی باشد." }
            require(cleanUrl.length >= 4) { "آدرس منبع معتبر نیست." }

            val docRef = db.collection("users").document(uid).collection("sources").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "url" to cleanUrl,
                "sourceType" to sourceType.name,
                "category" to cleanCategory,
                "logoUrl" to cleanLogo,
                "channelCount" to safeChannelCount,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSource(userId: String, sourceId: String): Result<Unit> {
        val docPath = "users/$userId/sources/$sourceId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "حساب کاربری تغییر کرده است." }
            require(sourceId.isNotBlank()) { "شناسه منبع معتبر نیست." }
            db.collection("users").document(uid).collection("sources").document(sourceId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(
        userId: String,
        title: String,
        streamUrl: String,
        posterUrl: String?,
        sourceName: String,
        mediaType: MediaType,
        currentlyFavorite: Boolean,
    ): Result<Boolean> {
        val itemId = stableDocIdForUrl(streamUrl)
        val docPath = "users/$userId/library/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "حساب کاربری تغییر کرده است." }
            val docRef = db.collection("users").document(uid).collection("library").document(itemId)
            val existing = docRef.get().await()
            val nextFavorite = !currentlyFavorite

            val cleanTitle = title.trim().ifBlank { "ویدیو" }.take(160)
            val cleanUrl = streamUrl.trim().take(1200)
            val cleanPoster = posterUrl?.trim()?.takeIf { it.isNotBlank() }?.take(1200)
            val cleanSource = sourceName.trim().ifBlank { "سینما استریم" }.take(100)

            require(cleanUrl.length >= 4) { "آدرس استریم نامعتبر است." }

            if (existing.exists()) {
                val updates = mapOf(
                    "title" to cleanTitle,
                    "posterUrl" to cleanPoster,
                    "sourceName" to cleanSource,
                    "mediaType" to mediaType.name,
                    "favorite" to nextFavorite,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "streamUrl" to cleanUrl,
                    "posterUrl" to cleanPoster,
                    "sourceName" to cleanSource,
                    "mediaType" to mediaType.name,
                    "favorite" to nextFavorite,
                    "lastPositionMs" to 0L,
                    "durationMs" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(nextFavorite)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveWatchProgress(
        userId: String,
        title: String,
        streamUrl: String,
        posterUrl: String?,
        sourceName: String,
        mediaType: MediaType,
        positionMs: Long,
        durationMs: Long,
    ): Result<Unit> {
        val itemId = stableDocIdForUrl(streamUrl)
        val docPath = "users/$userId/library/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "حساب کاربری تغییر کرده است." }
            val docRef = db.collection("users").document(uid).collection("library").document(itemId)
            val existing = docRef.get().await()

            val cleanTitle = title.trim().ifBlank { "ویدیو" }.take(160)
            val cleanUrl = streamUrl.trim().take(1200)
            val cleanPoster = posterUrl?.trim()?.takeIf { it.isNotBlank() }?.take(1200)
            val cleanSource = sourceName.trim().ifBlank { "سینما استریم" }.take(100)
            val safePos = positionMs.coerceAtLeast(0L)
            val safeDur = durationMs.coerceAtLeast(0L)

            if (cleanUrl.length < 4) return Result.success(Unit)

            if (existing.exists()) {
                val updates = mapOf(
                    "title" to cleanTitle,
                    "posterUrl" to cleanPoster,
                    "sourceName" to cleanSource,
                    "mediaType" to mediaType.name,
                    "lastPositionMs" to safePos,
                    "durationMs" to safeDur,
                    "lastWatchedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "streamUrl" to cleanUrl,
                    "posterUrl" to cleanPoster,
                    "sourceName" to cleanSource,
                    "mediaType" to mediaType.name,
                    "favorite" to false,
                    "lastPositionMs" to safePos,
                    "durationMs" to safeDur,
                    "lastWatchedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteLibraryItem(userId: String, itemId: String): Result<Unit> {
        val docPath = "users/$userId/library/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "حساب کاربری تغییر کرده است." }
            require(itemId.isNotBlank()) { "شناسه آیتم نامعتبر است." }
            db.collection("users").document(uid).collection("library").document(itemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        fun stableDocIdForUrl(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(url.trim().toByteArray(Charsets.UTF_8))
            return "m_" + digest.take(20).joinToString("") { "%02x".format(it) }
        }
    }
}
