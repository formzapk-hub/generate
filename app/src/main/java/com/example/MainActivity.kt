package com.example

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DocumentEntity
import com.example.receiver.NotificationHelper
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ExportUtil
import com.example.viewmodel.DocumentViewModel
import com.example.viewmodel.DocumentViewModelFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    Editor, History, Settings
}

enum class ExportFormat {
    DOCX, PDF, TXT
}

class MainActivity : ComponentActivity() {

    private val viewModel: DocumentViewModel by viewModels {
        DocumentViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize PDFBox resource loader
        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: DocumentViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Editor) }
    var selectedExportFormat by remember { mutableStateOf(ExportFormat.DOCX) }
    var showExportDialog by remember { mutableStateOf(false) }

    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val editorTitle by viewModel.editorTitle.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractionProgress by viewModel.extractionProgress.collectAsStateWithLifecycle()
    val extractionStatus by viewModel.extractionStatus.collectAsStateWithLifecycle()

    val pdfPagePreviews = remember { mutableStateListOf<Bitmap>() }
    var selectedPdfFileName by remember { mutableStateOf<String?>(null) }
    var isRenderingPreviews by remember { mutableStateOf(false) }

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var currentPreviewPageIndex by remember { mutableStateOf(0) }
    var totalPdfPages by remember { mutableStateOf(0) }
    var currentPdfPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPage by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPdfUri, currentPreviewPageIndex) {
        val uri = selectedPdfUri
        if (uri != null) {
            isRenderingPage = true
            val bitmap = withContext(Dispatchers.IO) {
                renderSinglePdfPageWithPdfBox(context, uri, currentPreviewPageIndex)
            }
            if (bitmap != null) {
                currentPdfPageBitmap = bitmap
            }
            isRenderingPage = false
        } else {
            currentPdfPageBitmap = null
            totalPdfPages = 0
            currentPreviewPageIndex = 0
        }
    }

    // Request permissions launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Izin notifikasi ditolak. Anda tidak akan menerima pengingat.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission request on startup if needed (Android 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // PDF Import Picker
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                // Extract file name
                var fileName = "Dokumen_PDF"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
                selectedPdfFileName = fileName
                selectedPdfUri = uri
                currentPreviewPageIndex = 0

                // Get total page count using PDFBox
                coroutineScope.launch(Dispatchers.IO) {
                    val count = getPdfPageCount(context, uri)
                    withContext(Dispatchers.Main) {
                        totalPdfPages = count
                    }
                }

                // Perform Extraction
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.extractTextFromPdf(inputStream, fileName, context)
                }

                // Render visual page previews using PDFBox in background
                pdfPagePreviews.clear()
                isRenderingPreviews = true
                coroutineScope.launch(Dispatchers.IO) {
                    val bitmaps = renderPdfPagesWithPdfBox(context, uri)
                    withContext(Dispatchers.Main) {
                        pdfPagePreviews.addAll(bitmaps)
                        isRenderingPreviews = false
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengimpor file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Image Import Picker (JPG, PNG, JPEG, dll)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                // Extract file name
                var fileName = "Dokumen_Gambar.png"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
                selectedPdfFileName = fileName
                selectedPdfUri = null // Reset PDF uri since we are converting an Image
                currentPreviewPageIndex = 0
                totalPdfPages = 1 // Images have 1 page preview
                pdfPagePreviews.clear()

                // Render image preview using Android's BitmapFactory
                isRenderingPreviews = true
                coroutineScope.launch(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            // Scale image preview for UI to save memory
                            val width = bitmap.width
                            val height = bitmap.height
                            val maxDimension = 300
                            val scale = maxDimension.toFloat() / maxOf(width, height)
                            val scaledBitmap = if (scale < 1f) {
                                Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
                            } else {
                                bitmap
                            }
                            pdfPagePreviews.add(scaledBitmap)
                            currentPdfPageBitmap = scaledBitmap
                        }
                        isRenderingPreviews = false
                    }
                }

                // Perform OCR Image text extraction
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.extractTextFromImage(inputStream, fileName, context)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengimpor gambar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // SAF Document Creator for exports
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    when (selectedExportFormat) {
                        ExportFormat.DOCX -> {
                            ExportUtil.saveTextToDocx(outputStream, editorText)
                            Toast.makeText(context, "Berhasil diekspor sebagai Word (.docx)!", Toast.LENGTH_LONG).show()
                        }
                        ExportFormat.PDF -> {
                            ExportUtil.saveTextToPdf(outputStream, editorText)
                            Toast.makeText(context, "Berhasil diekspor sebagai PDF (.pdf)!", Toast.LENGTH_LONG).show()
                        }
                        ExportFormat.TXT -> {
                            ExportUtil.saveTextToTxt(outputStream, editorText)
                            Toast.makeText(context, "Berhasil diekspor sebagai Teks (.txt)!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengekspor: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Adaptive Layout calculations (Responsive Design)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "PDF to Word Converter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Status: $saveStatus",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (saveStatus.contains("Tersimpan")) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        if (currentScreen == Screen.Editor) {
                            IconButton(
                                onClick = { pickPdfLauncher.launch("application/pdf") },
                                modifier = Modifier.testTag("import_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = "Impor PDF",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = { 
                                    selectedExportFormat = ExportFormat.DOCX
                                    showExportDialog = true 
                                },
                                enabled = editorText.isNotBlank(),
                                modifier = Modifier.testTag("export_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Ekspor Dokumen",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == Screen.Editor,
                            onClick = { currentScreen = Screen.Editor },
                            icon = { Icon(Icons.Default.Edit, contentDescription = "Editor") },
                            label = { Text("Editor") },
                            modifier = Modifier.testTag("nav_editor")
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.History,
                            onClick = { currentScreen = Screen.History },
                            icon = { Icon(Icons.Default.History, contentDescription = "Riwayat") },
                            label = { Text("Riwayat") },
                            modifier = Modifier.testTag("nav_history")
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.Settings,
                            onClick = { currentScreen = Screen.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                            label = { Text("Pengaturan") },
                            modifier = Modifier.testTag("nav_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NavigationRailItem(
                            selected = currentScreen == Screen.Editor,
                            onClick = { currentScreen = Screen.Editor },
                            icon = { Icon(Icons.Default.Edit, contentDescription = "Editor") },
                            label = { Text("Editor") },
                            modifier = Modifier.testTag("nav_rail_editor")
                        )
                        NavigationRailItem(
                            selected = currentScreen == Screen.History,
                            onClick = { currentScreen = Screen.History },
                            icon = { Icon(Icons.Default.History, contentDescription = "Riwayat") },
                            label = { Text("Riwayat") },
                            modifier = Modifier.testTag("nav_rail_history")
                        )
                        NavigationRailItem(
                            selected = currentScreen == Screen.Settings,
                            onClick = { currentScreen = Screen.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                            label = { Text("Pengaturan") },
                            modifier = Modifier.testTag("nav_rail_settings")
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (currentScreen) {
                        Screen.Editor -> {
                            EditorScreen(
                                viewModel = viewModel,
                                isExtracting = isExtracting,
                                extractionProgress = extractionProgress,
                                extractionStatus = extractionStatus,
                                pdfPagePreviews = pdfPagePreviews,
                                selectedPdfFileName = selectedPdfFileName,
                                isRenderingPreviews = isRenderingPreviews,
                                selectedPdfUri = selectedPdfUri,
                                currentPreviewPageIndex = currentPreviewPageIndex,
                                totalPdfPages = totalPdfPages,
                                currentPdfPageBitmap = currentPdfPageBitmap,
                                isRenderingPage = isRenderingPage,
                                onNextPageClick = { if (currentPreviewPageIndex < totalPdfPages - 1) currentPreviewPageIndex++ },
                                onPrevPageClick = { if (currentPreviewPageIndex > 0) currentPreviewPageIndex-- },
                                onPageSelected = { index -> currentPreviewPageIndex = index },
                                onPickPdfClick = { pickPdfLauncher.launch("application/pdf") },
                                onPickImageClick = { pickImageLauncher.launch("image/*") }
                            )
                        }
                        Screen.History -> {
                            HistoryScreen(
                                viewModel = viewModel,
                                onDocumentLoaded = { currentScreen = Screen.Editor }
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Dialog for exporting document
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "Ekspor Dokumen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pilih format dokumen tujuan ekspor Anda secara efisien:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedExportFormat = ExportFormat.DOCX },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedExportFormat == ExportFormat.DOCX) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedExportFormat == ExportFormat.DOCX) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DOCX")
                        }

                        Button(
                            onClick = { selectedExportFormat = ExportFormat.PDF },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedExportFormat == ExportFormat.PDF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedExportFormat == ExportFormat.PDF) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("PDF")
                        }

                        Button(
                            onClick = { selectedExportFormat = ExportFormat.TXT },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedExportFormat == ExportFormat.TXT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedExportFormat == ExportFormat.TXT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("TXT")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        val cleanTitle = editorTitle.replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
                        val extension = when (selectedExportFormat) {
                            ExportFormat.DOCX -> "docx"
                            ExportFormat.PDF -> "pdf"
                            ExportFormat.TXT -> "txt"
                        }
                        createDocumentLauncher.launch("$cleanTitle.$extension")
                    }
                ) {
                    Text("EKSPOR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("BATAL")
                }
            }
        )
    }
}

@Composable
fun EditorScreen(
    viewModel: DocumentViewModel,
    isExtracting: Boolean,
    extractionProgress: Float,
    extractionStatus: String,
    pdfPagePreviews: List<Bitmap>,
    selectedPdfFileName: String?,
    isRenderingPreviews: Boolean,
    selectedPdfUri: Uri?,
    currentPreviewPageIndex: Int,
    totalPdfPages: Int,
    currentPdfPageBitmap: Bitmap?,
    isRenderingPage: Boolean,
    onNextPageClick: () -> Unit,
    onPrevPageClick: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPickPdfClick: () -> Unit,
    onPickImageClick: () -> Unit
) {
    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val editorTitle by viewModel.editorTitle.collectAsStateWithLifecycle()
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()
    val replaceQuery by viewModel.replaceQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Source Selector: PDF or Image to Word
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // PDF to Word Card
            Card(
                onClick = onPickPdfClick,
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp)
                    .testTag("pdf_picker_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "PDF ke Word",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PDF ke Word",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Impor file .pdf",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Image to Word Card (Requested Feature: JPG, PNG, JPEG, dll)
            Card(
                onClick = onPickImageClick,
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp)
                    .testTag("image_picker_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Gambar ke Word",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Gambar ke Word",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Impor JPG, PNG, dll",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // File Status Info Indicator
        if (selectedPdfFileName != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (selectedPdfUri != null) Icons.Default.UploadFile else Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "File Terpilih: $selectedPdfFileName",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Active Loaders / Progress indicators for PDF parsing
        AnimatedVisibility(visible = isExtracting) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = extractionStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(extractionProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { extractionProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Document Visual Page Preview slider and dynamic page detailed viewer
        if (selectedPdfUri != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Penjelajah Pratinjau PDF (PDF Page Explorer)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Detailed Dynamic Viewer
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_detailed_preview_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentPdfPageBitmap != null) {
                                Image(
                                    bitmap = currentPdfPageBitmap.asImageBitmap(),
                                    contentDescription = "Detail Halaman ${currentPreviewPageIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (isRenderingPage) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Gagal memuat halaman", style = MaterialTheme.typography.bodySmall)
                            }

                            if (isRenderingPage && currentPdfPageBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPrevPageClick,
                                enabled = currentPreviewPageIndex > 0 && !isRenderingPage,
                                modifier = Modifier.testTag("prev_page_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Halaman Sebelumnya"
                                )
                            }

                            Text(
                                text = "Halaman ${currentPreviewPageIndex + 1} dari $totalPdfPages",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = onNextPageClick,
                                enabled = currentPreviewPageIndex < totalPdfPages - 1 && !isRenderingPage,
                                modifier = Modifier.testTag("next_page_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Halaman Selanjutnya"
                                )
                            }
                        }
                    }
                }

                // 2. Horizontal thumbnails selection
                if (pdfPagePreviews.isNotEmpty() || isRenderingPreviews) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Akses Cepat Halaman",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isRenderingPreviews) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text("Memuat akses cepat...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pdfPagePreviews.forEachIndexed { index, bitmap ->
                                    val isSelected = index == currentPreviewPageIndex
                                    Card(
                                        onClick = { onPageSelected(index) },
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.width(90.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Halaman ${index + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Hal ${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Find and Replace panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Cari & Ganti Kata",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { viewModel.updateFindQuery(it) },
                        label = { Text("Cari kata") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("find_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { viewModel.updateReplaceQuery(it) },
                        label = { Text("Ganti dengan") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("replace_input"),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        val count = viewModel.performFindReplace()
                        if (count > 0) {
                            Toast.makeText(context, "Berhasil mengganti $count kata!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Kata tidak ditemukan!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("replace_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GANTI SEMUA KATA")
                }
            }
        }

        // Document Editor Screen
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editor Dokumen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Karakter: ${editorText.length} | Kata: ${editorText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = editorTitle,
                onValueChange = { viewModel.updateEditorTitle(it) },
                label = { Text("Judul Dokumen") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("document_title_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = editorText,
                onValueChange = { viewModel.updateEditorText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .testTag("text_editor"),
                placeholder = { Text("Teks PDF akan muncul di sini setelah file dipilih atau Anda mengetik...") },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: DocumentViewModel,
    onDocumentLoaded: () -> Unit
) {
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Arsip Dokumen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = { 
                    viewModel.createNewDocument()
                    onDocumentLoaded()
                },
                modifier = Modifier.testTag("create_new_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Buat Baru",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Belum Ada Dokumen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Impor PDF atau buat teks baru untuk disimpan secara otomatis di sini",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents) { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectDocument(doc)
                                onDocumentLoaded()
                            }
                            .testTag("document_card_${doc.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (doc.content.isBlank()) "Tanpa isi" else doc.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (doc.syncStatus == 1) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (doc.syncStatus == 1) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                    Text(
                                        text = if (doc.syncStatus == 1) "Tersinkronisasi" else "Belum Sinkron",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (doc.syncStatus == 1) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { 
                                    viewModel.deleteDocument(doc.id)
                                    Toast.makeText(context, "Dokumen dihapus", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("delete_document_${doc.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Dokumen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: DocumentViewModel) {
    val context = LocalContext.current
    var deviceName by remember { mutableStateOf(Build.MODEL ?: "Perangkat Android") }
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    // Alarm States
    var reminderHour by remember { mutableStateOf(8) }
    var reminderMinute by remember { mutableStateOf(0) }
    var customReminderText by remember { mutableStateOf("Jangan lupa melanjutkan edit dokumen PDF Anda hari ini!") }
    var showAlarmActiveBadge by remember { mutableStateOf(false) }

    LaunchedEffect(syncMessage) {
        if (syncMessage != null) {
            Toast.makeText(context, syncMessage, Toast.LENGTH_LONG).show()
            viewModel.clearSyncMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Pengaturan & Sinkronisasi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Cloud Backup Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Sinkronisasi Awan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Cadangkan semua dokumen penting Anda ke awan agar dapat disinkronkan di berbagai perangkat secara instan.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Nama Perangkat") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = { viewModel.syncToCloud(deviceName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_button"),
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MENYINKRONKAN...")
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SINKRONISASI SEKARANG")
                    }
                }
            }
        }

        // Daily task reminders card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Pengingat Tugas Harian",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (showAlarmActiveBadge) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4CAF50), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Aktif",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "Konfigurasikan sistem notifikasi yang dapat dipersonalisasi untuk mengingatkan Anda mengedit file harian.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Pilih Waktu Pengingat:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Jam", style = MaterialTheme.typography.bodySmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (reminderHour > 0) reminderHour-- }) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format("%02d", reminderHour),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { if (reminderHour < 23) reminderHour++ }) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Menit", style = MaterialTheme.typography.bodySmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (reminderMinute > 0) reminderMinute -= 5 else reminderMinute = 55 }) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format("%02d", reminderMinute),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { if (reminderMinute < 55) reminderMinute += 5 else reminderMinute = 0 }) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customReminderText,
                    onValueChange = { customReminderText = it },
                    label = { Text("Pesan Notifikasi") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelReminder(context)
                            showAlarmActiveBadge = false
                            Toast.makeText(context, "Pengingat dinonaktifkan", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("NONAKTIFKAN")
                    }

                    Button(
                        onClick = {
                            viewModel.scheduleReminder(context, reminderHour, reminderMinute, customReminderText)
                            showAlarmActiveBadge = true
                            Toast.makeText(
                                context,
                                String.format("Pengingat dijadwalkan setiap pukul %02d:%02d!", reminderHour, reminderMinute),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("JADWALKAN")
                    }
                }
            }
        }
    }
}

/**
 * Renders up to first 5 pages of a PDF using PDFBox-Android's PDFRenderer to produce horizontal previews.
 */
fun renderPdfPagesWithPdfBox(context: Context, uri: Uri): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    var document: PDDocument? = null
    var inputStream: java.io.InputStream? = null
    try {
        inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            document = PDDocument.load(inputStream)
            val renderer = PDFRenderer(document)
            val pageCount = minOf(document.numberOfPages, 5) // Limit to 5 pages for maximum performance
            for (i in 0 until pageCount) {
                // Render at a scale of 0.5f to keep memory usage low and render fast
                val bitmap = renderer.renderImage(i, 0.5f, ImageType.ARGB)
                if (bitmap != null) {
                    bitmaps.add(bitmap)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            document?.close()
        } catch (e: Exception) {}
        try {
            inputStream?.close()
        } catch (e: Exception) {}
    }
    return bitmaps
}

/**
 * Renders a single page of a PDF using PDFBox-Android's PDFRenderer.
 */
fun renderSinglePdfPageWithPdfBox(context: Context, uri: Uri, pageIndex: Int): Bitmap? {
    var document: PDDocument? = null
    var inputStream: java.io.InputStream? = null
    try {
        inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            document = PDDocument.load(inputStream)
            if (pageIndex >= 0 && pageIndex < document.numberOfPages) {
                val renderer = PDFRenderer(document)
                // Use a scale of 0.8f for a larger, higher-quality detailed preview
                return renderer.renderImage(pageIndex, 0.8f, ImageType.ARGB)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            document?.close()
        } catch (e: Exception) {}
        try {
            inputStream?.close()
        } catch (e: Exception) {}
    }
    return null
}

/**
 * Retrieves the total number of pages of a PDF using PDFBox-Android.
 */
fun getPdfPageCount(context: Context, uri: Uri): Int {
    var document: PDDocument? = null
    var inputStream: java.io.InputStream? = null
    try {
        inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            document = PDDocument.load(inputStream)
            return document.numberOfPages
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            document?.close()
        } catch (e: Exception) {}
        try {
            inputStream?.close()
        } catch (e: Exception) {}
    }
    return 0
}
