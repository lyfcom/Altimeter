package com.altimeter.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.altimeter.app.data.models.AltitudeRecord
import com.altimeter.app.data.models.AltitudeStatistics
import java.io.File
import java.io.FileWriter
import java.time.format.DateTimeFormatter

/**
 * 分享助手类
 * 处理海拔数据的分享功能
 */
object ShareHelper {
    
    /**
     * 分享海拔统计数据（文本格式）
     */
    fun shareStatistics(
        context: Context,
        statistics: AltitudeStatistics,
        @Suppress("UNUSED_PARAMETER")
        records: List<AltitudeRecord>
    ) {
        val shareText = buildString {
            appendLine("📍 海拔高度计数据报告")
            appendLine("═══════════════════")
            appendLine()
            
            // 基本统计
            appendLine("📊 统计概览:")
            appendLine("• 总记录数: ${statistics.totalRecords}")
            appendLine("• 总测量会话: ${statistics.totalSessions}")
            appendLine("• 平均海拔: ${String.format("%.1f", statistics.averageAltitude)}米")
            appendLine("• 最高海拔: ${String.format("%.1f", statistics.maxAltitude)}米")
            appendLine("• 最低海拔: ${String.format("%.1f", statistics.minAltitude)}米")
            appendLine("• 海拔范围: ${String.format("%.1f", statistics.maxAltitude - statistics.minAltitude)}米")
            appendLine("• 最可靠数据源: ${statistics.mostReliableSource.displayName}")
            
            statistics.firstRecordTime?.let { firstTime ->
                statistics.lastRecordTime?.let { lastTime ->
                    appendLine("• 记录时间段: ${firstTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} 至 ${lastTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                }
            }
            
            appendLine()
            appendLine("📱 来自海拔高度计APP")
        }
        
        shareText(context, shareText, "海拔数据统计")
    }
    
    /**
     * 分享海拔记录数据（CSV格式）
     */
    fun shareRecordsAsCSV(
        context: Context,
        records: List<AltitudeRecord>
    ) {
        try {
            val csvFile = createCSVFile(context, records)
            shareFile(context, csvFile, "海拔记录数据.csv", "text/csv")
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果文件分享失败，退回到文本分享
            shareRecordsAsText(context, records)
        }
    }
    
    /**
     * 分享海拔记录数据（文本格式）
     */
    fun shareRecordsAsText(
        context: Context,
        records: List<AltitudeRecord>
    ) {
        val shareText = buildString {
            appendLine("📍 海拔记录详情")
            appendLine("═══════════════════")
            appendLine()
            
            if (records.isEmpty()) {
                appendLine("暂无记录数据")
            } else {
                records.sortedByDescending { it.timestamp }.take(50).forEach { record ->
                    appendLine("🏔️ ${String.format("%.1f", record.altitude)}米")
                    appendLine("📅 ${record.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                    appendLine("📍 位置: ${String.format("%.6f", record.latitude)}, ${String.format("%.6f", record.longitude)}")
                    appendLine("📊 数据源: ${record.source.displayName}")
                    appendLine("🎯 可靠度: ${String.format("%.0f", record.reliability)}%")
                    appendLine("📏 精度: ±${String.format("%.1f", record.accuracy)}米")
                    if (record.description.isNotEmpty()) {
                        appendLine("📝 说明: ${record.description}")
                    }
                    appendLine("─────────────────")
                    appendLine()
                }
                
                if (records.size > 50) {
                    appendLine("... 显示最近50条记录，共${records.size}条")
                    appendLine()
                }
            }
            
            appendLine("📱 来自海拔高度计APP")
        }
        
        shareText(context, shareText, "海拔记录数据")
    }
    
    /**
     * 创建CSV文件
     */
    private fun createCSVFile(context: Context, records: List<AltitudeRecord>): File {
        val fileName = "altitude_records_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        FileWriter(file).use { writer ->
            // CSV头部
            writer.append("时间,海拔(米),纬度,经度,数据源,可靠度(%),精度(米),会话ID,描述\n")
            
            // 数据行
            records.forEach { record ->
                writer.append("${record.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))},")
                writer.append("${record.altitude},")
                writer.append("${record.latitude},")
                writer.append("${record.longitude},")
                writer.append("${record.source.displayName},")
                writer.append("${String.format("%.1f", record.reliability)},")
                writer.append("${String.format("%.1f", record.accuracy)},")
                writer.append("${record.sessionId ?: ""},")
                writer.append("\"${record.description.replace("\"", "\"\"")}\"\n")
            }
            
            writer.flush()
        }
        
        return file
    }
    
    /**
     * 分享文本内容
     */
    private fun shareText(context: Context, text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        
        val chooser = Intent.createChooser(intent, "分享海拔数据")
        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(chooser)
    }
    
    /**
     * 分享文件
     */
    private fun shareFile(context: Context, file: File, fileName: String, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, "分享海拔数据文件")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果FileProvider失败，使用普通URI
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
            }
            
            val chooser = Intent.createChooser(intent, "分享海拔数据文件")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
        }
    }
}