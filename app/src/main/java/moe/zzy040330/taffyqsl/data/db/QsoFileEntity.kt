package moe.zzy040330.taffyqsl.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ADIF files created within the app.
 */
@Entity(tableName = "qso_files")
data class QsoFileEntity(
    @PrimaryKey val fileName: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val qsoCount: Int = 0
)
