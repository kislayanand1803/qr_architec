package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QrDao {
    @Query("SELECT * FROM qr_codes ORDER BY creationDate DESC")
    fun getAllQrCodes(): Flow<List<QrCode>>

    @Query("SELECT * FROM qr_codes WHERE id = :id")
    fun getQrCodeById(id: Int): Flow<QrCode?>

    @Query("SELECT * FROM qr_codes WHERE id = :id")
    suspend fun getQrCodeByIdSuspend(id: Int): QrCode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQrCode(qrCode: QrCode): Long

    @Update
    suspend fun updateQrCode(qrCode: QrCode)

    @Delete
    suspend fun deleteQrCode(qrCode: QrCode)

    @Query("DELETE FROM qr_codes WHERE id = :id")
    suspend fun deleteQrCodeById(id: Int)

    // Analytics Scan Logs
    @Query("SELECT * FROM scan_logs ORDER BY timestamp DESC")
    fun getAllScanLogs(): Flow<List<ScanLog>>

    @Query("SELECT * FROM scan_logs WHERE qrId = :qrId ORDER BY timestamp DESC")
    fun getScanLogsForQr(qrId: Int): Flow<List<ScanLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanLog(scanLog: ScanLog)
    
    @Query("UPDATE qr_codes SET scanCount = scanCount + 1 WHERE id = :qrId")
    suspend fun incrementScanCount(qrId: Int)
}
