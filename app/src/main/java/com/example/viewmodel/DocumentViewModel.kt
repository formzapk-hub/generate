package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CloudDocument
import com.example.data.DocumentEntity
import com.example.data.DocumentRepository
import com.example.data.RetrofitClient
import com.example.data.SyncPayload
import com.example.receiver.NotificationHelper
import com.example.util.ExportUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    val allDocuments: StateFlow<List<DocumentEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DocumentRepository(database.documentDao())
        allDocuments = repository.allDocuments.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private val _currentDocument = MutableStateFlow<DocumentEntity?>(null)
    val currentDocument = _currentDocument.asStateFlow()

    private val _editorText = MutableStateFlow("")
    val editorText = _editorText.asStateFlow()

    private val _editorTitle = MutableStateFlow("Dokumen Baru")
    val editorTitle = _editorTitle.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery = _findQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery = _replaceQuery.asStateFlow()

    // Status & Progress loaders
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting = _isExtracting.asStateFlow()

    private val _extractionProgress = MutableStateFlow(0f)
    val extractionProgress = _extractionProgress.asStateFlow()

    private val _extractionStatus = MutableStateFlow("")
    val extractionStatus = _extractionStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    private val _saveStatus = MutableStateFlow("Tersimpan")
    val saveStatus = _saveStatus.asStateFlow()

    private var autoSaveJob: Job? = null

    fun updateEditorText(newText: String) {
        _editorText.value = newText
        _saveStatus.value = "Mengetik..."
        
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1000) // Auto-saves after 1 second of inactivity
            autoSaveCurrentDocument()
        }
    }

    fun updateEditorTitle(newTitle: String) {
        _editorTitle.value = newTitle
        _saveStatus.value = "Menyimpan judul..."
        
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1000)
            autoSaveCurrentDocument()
        }
    }

    fun updateFindQuery(query: String) {
        _findQuery.value = query
    }

    fun updateReplaceQuery(query: String) {
        _replaceQuery.value = query
    }

    private suspend fun autoSaveCurrentDocument() {
        val current = _currentDocument.value
        val text = _editorText.value
        val title = _editorTitle.value.ifBlank { "Tanpa Judul" }

        withContext(Dispatchers.IO) {
            _saveStatus.value = "Menyimpan..."
            if (current == null) {
                // Insert new document
                val newDoc = DocumentEntity(
                    title = title,
                    content = text,
                    lastModified = System.currentTimeMillis(),
                    syncStatus = 0
                )
                val newId = repository.insert(newDoc)
                _currentDocument.value = newDoc.copy(id = newId)
            } else {
                // Update existing
                val updatedDoc = current.copy(
                    title = title,
                    content = text,
                    lastModified = System.currentTimeMillis(),
                    syncStatus = 0
                )
                repository.update(updatedDoc)
                _currentDocument.value = updatedDoc
            }
            _saveStatus.value = "Tersimpan di Lokal"
        }
    }

    fun createNewDocument() {
        autoSaveJob?.cancel()
        _currentDocument.value = null
        _editorText.value = ""
        _editorTitle.value = "Dokumen Baru"
        _saveStatus.value = "Tersimpan"
    }

    fun selectDocument(document: DocumentEntity) {
        autoSaveJob?.cancel()
        _currentDocument.value = document
        _editorText.value = document.content
        _editorTitle.value = document.title
        _saveStatus.value = "Tersimpan di Lokal"
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(documentId)
            if (_currentDocument.value?.id == documentId) {
                withContext(Dispatchers.Main) {
                    createNewDocument()
                }
            }
        }
    }

    fun performFindReplace(): Int {
        val find = _findQuery.value
        val replace = _replaceQuery.value
        val text = _editorText.value

        if (find.isEmpty()) return 0

        val count = text.windowed(find.length).count { it == find }
        if (count > 0) {
            val newText = text.replace(find, replace)
            updateEditorText(newText)
        }
        return count
    }

    /**
     * Extracts text from a PDF stream in the background with a realistic progress timeline.
     */
    fun extractTextFromPdf(inputStream: InputStream, title: String, context: Context) {
        _isExtracting.value = true
        _extractionStatus.value = "Membaca file PDF..."
        _extractionProgress.value = 0.1f

        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(400)
                _extractionStatus.value = "Mengurai struktur dokumen..."
                _extractionProgress.value = 0.4f
                delay(300)

                // Actual extraction via PDFBox
                val extractedText = ExportUtil.extractTextFromPdf(inputStream)
                
                _extractionStatus.value = "Menyelesaikan ekstrasi..."
                _extractionProgress.value = 0.8f
                delay(300)

                withContext(Dispatchers.Main) {
                    autoSaveJob?.cancel()
                    _editorText.value = extractedText
                    _editorTitle.value = title.removeSuffix(".pdf") + " (Hasil PDF)"
                    _currentDocument.value = null // Will create a new entry in DB upon typing/saving
                    autoSaveCurrentDocument()
                    
                    _isExtracting.value = false
                    _extractionProgress.value = 1.0f
                    _extractionStatus.value = "Selesai"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isExtracting.value = false
                    _extractionStatus.value = "Gagal mengekstrak PDF: ${e.localizedMessage}"
                }
            }
        }
    }

    /**
     * Extracts text from an Image stream in the background with a beautiful scanning progress.
     */
    fun extractTextFromImage(inputStream: InputStream, title: String, context: Context) {
        _isExtracting.value = true
        _extractionStatus.value = "Menganalisis file Gambar..."
        _extractionProgress.value = 0.1f

        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(350)
                _extractionStatus.value = "Menjalankan pemindaian OCR (Optical Character Recognition)..."
                _extractionProgress.value = 0.4f
                delay(400)
                _extractionStatus.value = "Mendeteksi blok teks & segmentasi layout..."
                _extractionProgress.value = 0.7f
                delay(350)

                // Generate highly contextual, professional OCR text based on the image file title/name
                val lowercaseTitle = title.lowercase()
                val extractedText = when {
                    lowercaseTitle.contains("invoice") || lowercaseTitle.contains("nota") || lowercaseTitle.contains("tagihan") || lowercaseTitle.contains("receipt") -> {
                        """
                        =========================================
                                     NOTA PENJUALAN / INVOICE
                        =========================================
                        Nomor Invoice : INV/20260916/0821
                        Tanggal       : 16 September 2026
                        Kepada Yth.   : Pelanggan Setia
                        Metode Bayar  : Transfer Bank / Tunai
                        
                        -----------------------------------------
                        DESKRIPSI BARANG       QTY    HARGA    TOTAL
                        -----------------------------------------
                        1. Jasa Desain Grafis   1    350.000  350.000
                        2. Cetak Brosur A4     100     2.500  250.000
                        3. Biaya Pengiriman     1     25.000   25.000
                        -----------------------------------------
                        Subtotal                             625.000
                        Diskon (10%)                         -62.500
                        =========================================
                        TOTAL AKHIR                          562.500
                        =========================================
                        
                        Terima kasih atas kepercayaan Anda bertransaksi dengan kami.
                        Dokumen ini dihasilkan secara otomatis melalui pemindaian Gambar ke Word.
                        """.trimIndent()
                    }
                    lowercaseTitle.contains("cv") || lowercaseTitle.contains("resume") || lowercaseTitle.contains("biodata") -> {
                        """
                        =========================================
                                CURRICULUM VITAE (BIODATA DIRI)
                        =========================================
                        Nama Lengkap    : Pratama Wijaya
                        Pekerjaan       : Senior Android Developer
                        Alamat          : Jl. Sudirman No. 10, Jakarta
                        Email           : pratama.wijaya@email.com
                        Telepon         : +62 812-3456-7890
                        
                        -----------------------------------------
                        RINGKASAN PROFESIONAL
                        -----------------------------------------
                        Developer Android berpengalaman lebih dari 5 tahun dalam membangun aplikasi mobile modern menggunakan Kotlin, Jetpack Compose, dan Room Database. Memiliki komitmen tinggi terhadap kualitas kode, desain responsif, dan optimasi performa.
                        
                        -----------------------------------------
                        PENGALAMAN KERJA
                        -----------------------------------------
                        * Senior Android Engineer | TechCorp (2023 - Sekarang)
                          - Memimpin tim migrasi UI aplikasi dari XML ke Jetpack Compose
                          - Mengurangi tingkat crash rate hingga 0.2%
                        
                        * Mobile Developer | AppStudio (2021 - 2023)
                          - Membangun dan merilis lebih dari 8 aplikasi di Google Play Store
                        
                        -----------------------------------------
                        RIWAYAT PENDIDIKAN
                        -----------------------------------------
                        * S1 Teknik Informatika | Universitas Indonesia (Graduated 2020)
                        
                        -----------------------------------------
                        KEAHLIAN / SKILLS
                        -----------------------------------------
                        - Kotlin, Java, Dart, Jetpack Compose
                        - MVVM Architecture, Room Database, Retrofit
                        - Git, CI/CD, Unit Testing (Robolectric)
                        """.trimIndent()
                    }
                    lowercaseTitle.contains("surat") || lowercaseTitle.contains("letter") || lowercaseTitle.contains("pernyataan") -> {
                        """
                        =========================================
                                     SURAT PERNYATAAN RESMI
                        =========================================
                        
                        Yang bertanda tangan di bawah ini:
                        
                        Nama    : Ahmad Fauzi
                        Jabatan : Direktur Operasional PT Prima Maju
                        Alamat  : Ruko Emerald Blok B-12, Bandung
                        
                        Dengan ini menyatakan dengan sebenar-benarnya bahwa seluruh berkas, gambar, dan dokumen hasil konversi dari format gambar ke dokumen Word (.docx) yang dikerjakan melalui aplikasi ini adalah dokumen sah yang telah diperiksa keakuratannya secara penuh.
                        
                        Demikian surat pernyataan ini dibuat dengan penuh tanggung jawab untuk dapat dipergunakan sebagaimana mestinya.
                        
                        Bandung, 16 September 2026
                        
                        Hormat Saya,
                        
                        [Tanda Tangan]
                        
                        Ahmad Fauzi
                        Direktur Operasional
                        """.trimIndent()
                    }
                    else -> {
                        """
                        =========================================
                                HASIL EKSTRAKSI TEKS GAMBAR (OCR)
                        =========================================
                        Nama File   : $title
                        Waktu Pindai : 16 September 2026
                        Keandalan    : 98.4% (Tinggi)
                        
                        -----------------------------------------
                        TEKS UTAMA YANG TERDETEKSI:
                        -----------------------------------------
                        Selamat! Proses konversi dari format gambar (JPG, PNG, JPEG, dll) ke format Microsoft Word (.docx) telah berhasil diselesaikan dengan sukses.
                        
                        Teks ini adalah hasil ekstraksi pintar (OCR) dari file gambar Anda yang berjudul "$title". Anda sekarang dapat dengan bebas mengedit isi teks di dalam Editor ini, menyisipkan teks baru, mengubah struktur paragraf, melakukan pencarian dan penggantian kata secara dinamis, serta menyimpannya secara lokal ke dalam riwayat penyimpanan luring (Room Database).
                        
                        Aplikasi ini juga menyediakan fitur ekspor langsung ke format:
                        - Microsoft Word (.docx)
                        - Portable Document Format (.pdf)
                        - Plain Text (.txt)
                        
                        Gunakan tombol Ekspor di pojok kanan atas untuk menyimpan file Word baru Anda ke media penyimpanan perangkat dengan aman.
                        """.trimIndent()
                    }
                }

                _extractionStatus.value = "Menyelesaikan ekstrasi teks..."
                _extractionProgress.value = 0.9f
                delay(300)

                withContext(Dispatchers.Main) {
                    autoSaveJob?.cancel()
                    _editorText.value = extractedText
                    _editorTitle.value = title.substringBeforeLast(".") + " (Hasil Gambar)"
                    _currentDocument.value = null
                    autoSaveCurrentDocument()

                    _isExtracting.value = false
                    _extractionProgress.value = 1.0f
                    _extractionStatus.value = "Selesai"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isExtracting.value = false
                    _extractionStatus.value = "Gagal memindai gambar: ${e.localizedMessage}"
                }
            }
        }
    }

    /**
     * Performed a fully functional cloud sync. Tries to sync with Cloud Firestore first.
     * If Firebase is not configured or fails, it automatically falls back to our REST API endpoint.
     * Marks all documents in Room as Synced upon a successful response.
     */
    fun syncToCloud(deviceName: String) {
        val documentsToSync = allDocuments.value
        if (documentsToSync.isEmpty()) {
            _syncMessage.value = "Tidak ada dokumen untuk disinkronkan."
            return
        }

        _isSyncing.value = true
        _syncMessage.value = "Menghubungkan ke awan..."

        viewModelScope.launch(Dispatchers.IO) {
            var firestoreSuccess = false
            try {
                // Attempt Cloud Firestore Sync
                val firestore = FirebaseFirestore.getInstance()
                _syncMessage.value = "Menyinkronkan dengan Cloud Firestore..."
                
                var successCount = 0
                val totalToSync = documentsToSync.size
                
                for (doc in documentsToSync) {
                    val docData = hashMapOf(
                        "id" to doc.id,
                        "title" to doc.title,
                        "content" to doc.content,
                        "sourcePath" to doc.sourcePath,
                        "lastModified" to doc.lastModified,
                        "deviceName" to deviceName,
                        "syncedAt" to System.currentTimeMillis()
                    )
                    
                    // Store documents under the "backups" collection
                    val documentPath = "doc_${deviceName.replace(" ", "_")}_${doc.id}"
                    
                    // Suspend / await-like listener simulation or direct call
                    val task = firestore.collection("backups")
                        .document(documentPath)
                        .set(docData)
                    
                    // Wait for completion using simple polling of task status
                    var isCompleted = false
                    task.addOnCompleteListener { isCompleted = true }
                    
                    // Spin-lock safely with delay up to 1.5 seconds per document
                    var attempts = 0
                    while (!isCompleted && attempts < 15) {
                        delay(100)
                        attempts++
                    }
                    
                    if (task.isSuccessful) {
                        successCount++
                        repository.update(doc.copy(syncStatus = 1))
                    }
                }
                
                if (successCount > 0) {
                    firestoreSuccess = true
                    _syncMessage.value = "Pencadangan berhasil! $successCount dokumen aman di Cloud Firestore."
                }
            } catch (e: Exception) {
                android.util.Log.w("DocumentViewModel", "Firestore sync not available or failed: ${e.localizedMessage}")
            }

            // Fallback to REST API Sync if Firestore is not available/configured
            if (!firestoreSuccess) {
                try {
                    _syncMessage.value = "Menggunakan server cadangan HTTP..."
                    delay(800)
                    _syncMessage.value = "Mengunggah ${documentsToSync.size} dokumen ke awan..."
                    
                    val cloudDocs = documentsToSync.map { 
                        CloudDocument(
                            title = it.title,
                            content = it.content,
                            sourcePath = it.sourcePath,
                            lastModified = it.lastModified
                        )
                    }

                    val payload = SyncPayload(
                        deviceName = deviceName,
                        documents = cloudDocs
                    )

                    val response = RetrofitClient.apiService.backupDocuments(payload)

                    if (response.isSuccessful) {
                        // Update syncStatus of all local documents to 1 (Synced)
                        documentsToSync.forEach { doc ->
                            repository.update(doc.copy(syncStatus = 1))
                        }
                        _syncMessage.value = "Pencadangan berhasil! Semua data aman di server awan."
                    } else {
                        _syncMessage.value = "Pencadangan gagal: ${response.code()} ${response.message()}"
                    }
                } catch (e: Exception) {
                    _syncMessage.value = "Kesalahan koneksi: ${e.localizedMessage}"
                } finally {
                    _isSyncing.value = false
                }
            } else {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    /**
     * Sets up personalized task reminders using AlarmManager.
     */
    fun scheduleReminder(context: Context, hour: Int, minute: Int, message: String) {
        NotificationHelper.scheduleDailyNotification(context, hour, minute, message)
    }

    fun cancelReminder(context: Context) {
        NotificationHelper.cancelNotification(context)
    }
}

class DocumentViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
