package moe.zzy040330.taffyqsl.domain.model

import moe.zzy040330.taffyqsl.data.db.DuplicateQsoEntity

data class SigningResult(
    val totalQsos: Int,
    val signedQsos: Int,
    val duplicateQsos: Int,
    val dateFilteredQsos: Int,
    val invalidQsos: Int,
    val gridMismatchQsos: Int = 0,
    val outputFile: java.io.File? = null,
    val uploadResponse: String? = null,

    // Entities to commit to DB only when the user saves or uploads the result.
    val pendingDupeEntities: List<DuplicateQsoEntity> = emptyList()
)

sealed class SigningProgress {
    data class Processing(val current: Int, val total: Int) : SigningProgress()
    data class Completed(val result: SigningResult) : SigningProgress()
    data class Failed(val error: String) : SigningProgress()
}
