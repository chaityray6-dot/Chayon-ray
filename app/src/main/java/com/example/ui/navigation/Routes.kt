package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object FilesRoute

@Serializable
data object ToolsRoute

@Serializable
data object AiRoute

@Serializable
data object SettingsRoute

@Serializable
data class ReaderRoute(val documentId: Long, val initialPage: Int = 0, val initialSharpen: Boolean = false)

@Serializable
data class EditorRoute(val documentId: Long, val initialPage: Int = 0)

@Serializable
data object ScannerRoute

@Serializable
data object ImageToPdfRoute

@Serializable
data object EnhancePdfRoute

@Serializable
data object OfficeConverterRoute

@Serializable
data object WebAndNotesToPdfRoute

@Serializable
data class MergeSplitOrganizeRoute(val mode: String = "merge")

@Serializable
data class CompressSecurityRoute(val mode: String = "compress")

@Serializable
data object OcrToolRoute

@Serializable
data class DocumentsSearchRoute(val initialQuery: String = "")
