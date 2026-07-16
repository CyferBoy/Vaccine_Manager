package com.clinic.neochild.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.clinic.neochild.R
import com.clinic.neochild.domain.model.Consultation
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.core.utils.PatientUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReceiptManager {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 421
    private const val MARGIN = 30f
    
    @Volatile
    private var logoCache: Bitmap? = null

    private fun getLogo(context: Context): Bitmap? {
        if (logoCache == null) {
            synchronized(this) {
                if (logoCache == null) {
                    logoCache = ContextCompat.getDrawable(context, R.drawable.logo)?.toBitmap()
                }
            }
        }
        return logoCache
    }

    suspend fun downloadReceipt(context: Context, patient: Patient, vaccination: Vaccination) {
        withContext(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                drawReceiptContent(context, page.canvas, patient, vaccination)
                
                pdfDocument.finishPage(page)

                val fileName = "Receipt_${patient.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                writePdfToStorage(context, pdfDocument, fileName, "Receipt downloaded to Downloads folder")
            } finally {
                pdfDocument.close()
            }
        }
    }

    fun printReceipt(context: Context, patient: Patient, vaccination: Vaccination) {
        val jobName = "Receipt_${patient.name}_${vaccination.dateGiven}"
        startPrintJob(context, jobName) { canvas ->
            drawReceiptContent(context, canvas, patient, vaccination)
        }
    }

    fun printConsultationReceipt(context: Context, patient: Patient, consultation: Consultation) {
        val jobName = "Consultation_${patient.name}_${consultation.date}"
        startPrintJob(context, jobName) { canvas ->
            drawConsultationContent(context, canvas, patient, consultation)
        }
    }

    private suspend fun writePdfToStorage(context: Context, pdfDocument: PdfDocument, fileName: String, successMessage: String) {
        try {
            val outputStream: OutputStream? = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri: Uri? = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { context.contentResolver.openOutputStream(it) }
                } else {
                    val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    FileOutputStream(target)
                }
            }

            outputStream?.use { 
                pdfDocument.writeTo(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            pdfDocument.close()
        }
    }

    private fun startPrintJob(context: Context, jobName: String, drawContent: (Canvas) -> Unit) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                val pdi = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback.onLayoutFinished(pdi, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                drawContent(page.canvas)
                
                pdfDocument.finishPage(page)

                try {
                    pdfDocument.writeTo(FileOutputStream(destination?.fileDescriptor))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                    return
                } finally {
                    pdfDocument.close()
                }

                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    private fun drawClinicHeader(context: Context, canvas: Canvas, paint: Paint, yPos: Float): Float {
        var currentY = yPos
        val logo = getLogo(context)
        if (logo != null) {
            val logoSize = 60f
            val destRect = RectF(MARGIN, currentY, MARGIN + logoSize, currentY + logoSize)
            canvas.drawBitmap(logo, null, destRect, paint)
            
            val textLeft = MARGIN + logoSize + 15f
            paint.typeface = Typeface.create("serif", Typeface.BOLD)
            paint.textSize = 16f
            paint.color = Color.BLACK
            canvas.drawText("Neo Child Clinic, Sahibganj", textLeft, currentY + 15f, paint)
            
            paint.typeface = Typeface.create("serif", Typeface.NORMAL)
            paint.textSize = 9f
            canvas.drawText("Old hospital road, Bengali Tola, Sahibganj, Jharkhand 816109", textLeft, currentY + 30f, paint)
            canvas.drawText("Mob: 6203646653, 7033905266", textLeft, currentY + 45f, paint)
            
            currentY += 70f
        }
        return currentY
    }

    private fun drawPatientInfoBar(canvas: Canvas, paint: Paint, patient: Patient, yPos: Float): Float {
        var currentY = yPos
        paint.color = Color.rgb(245, 245, 245)
        
        val nameLabel = "Patient: "
        val phoneX = PAGE_WIDTH.toFloat() - MARGIN - 10f
        val maxCombinedWidth = (phoneX - (MARGIN + 10f)) - 10f
        
        paint.textSize = 10f
        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        
        val age = PatientUtils.calculateAgeLabel(patient.dob) ?: ""
        val ageSexText = "Age/Sex: $age / ${patient.gender}"
        val ageWidth = paint.measureText(ageSexText) + 20f
        
        val maxNameWidth = maxCombinedWidth - ageWidth
        val nameLines = wrapText(patient.name, maxNameWidth - paint.measureText(nameLabel), paint)
        
        val infoBoxHeight = (nameLines.size * 12f + 8f).coerceAtLeast(25f)
        canvas.drawRect(MARGIN, currentY, PAGE_WIDTH.toFloat() - MARGIN, currentY + infoBoxHeight, paint)
        
        paint.color = Color.BLACK
        val firstLine = nameLabel + (nameLines.firstOrNull() ?: "")
        canvas.drawText(firstLine, MARGIN + 10f, currentY + 17f, paint)
        
        if (nameLines.size > 1) {
            for (i in 1 until nameLines.size) {
                canvas.drawText(nameLines[i], MARGIN + 10f + paint.measureText(nameLabel), currentY + 17f + (i * 12f), paint)
            }
        }
        
        val nameEndX = MARGIN + 10f + paint.measureText(firstLine) + 20f
        val dynamicAgeX = nameEndX.coerceAtLeast(MARGIN + 200f)
        
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        canvas.drawText(ageSexText, dynamicAgeX, currentY + 17f, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Ph: ${patient.phone}", phoneX, currentY + 17f, paint)
        paint.textAlign = Paint.Align.LEFT
        
        if (patient.address.isNotBlank()) {
            paint.typeface = Typeface.create("serif", Typeface.NORMAL)
            paint.textSize = 8f
            val addressLines = wrapText("Address: ${patient.address}", PAGE_WIDTH.toFloat() - (2 * MARGIN) - 20f, paint)
            addressLines.forEachIndexed { index, line ->
                canvas.drawText(line, MARGIN + 10f, currentY + infoBoxHeight + 8f + (index * 10f), paint)
            }
            currentY += (addressLines.size * 10f)
        }
        
        return currentY + infoBoxHeight + 12f
    }

    private fun drawConsultationContent(context: Context, canvas: Canvas, patient: Patient, consultation: Consultation) {
        val paint = Paint()
        var yPos = 40f
        val pageWidth = PAGE_WIDTH.toFloat()

        yPos = drawClinicHeader(context, canvas, paint, yPos)

        // Metadata
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        paint.textSize = 20f
        paint.color = Color.rgb(200, 200, 200)
        canvas.drawText("CONSULTATION", pageWidth - MARGIN, 55f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 9f
        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        canvas.drawText("No: #CON-${consultation.id.takeLast(6).uppercase()}", pageWidth - MARGIN, 75f, paint)
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        canvas.drawText("Date: ${PatientUtils.formatDateForDisplay(consultation.date)}", pageWidth - MARGIN, 87f, paint)
        
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Time: $timeStr", pageWidth - MARGIN, 99f, paint)
        
        paint.textAlign = Paint.Align.LEFT
        paint.strokeWidth = 1.5f
        canvas.drawLine(MARGIN, yPos, pageWidth - MARGIN, yPos, paint)
        yPos += 20f

        yPos = drawPatientInfoBar(canvas, paint, patient, yPos)

        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        paint.textSize = 9f
        canvas.drawText("Consultation by: Dr. Farogh Hassan (MBBS, DCH, DNB(Paediatrics), PGDDN)", MARGIN, yPos, paint)
        yPos += 18f

        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("Consultation Fee", MARGIN, yPos, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.getDefault(), "%.2f", consultation.amount)}", pageWidth - MARGIN, yPos, paint)
        
        paint.textAlign = Paint.Align.LEFT
        yPos += 20f
        canvas.drawLine(MARGIN, yPos, pageWidth - MARGIN, yPos, paint)
        
        if (consultation.notes.isNotBlank()) {
            yPos += 20f
            paint.typeface = Typeface.create("serif", Typeface.BOLD)
            paint.textSize = 10f
            canvas.drawText("Notes:", MARGIN, yPos, paint)
            
            yPos += 15f
            paint.typeface = Typeface.create("serif", Typeface.NORMAL)
            paint.textSize = 9f
            val noteLines = wrapText(consultation.notes, pageWidth - (2 * MARGIN), paint)
            noteLines.forEachIndexed { index, line ->
                canvas.drawText(line, MARGIN, yPos + (index * 12f), paint)
            }
            yPos += (noteLines.size * 12f)
        }

        if (consultation.nextFollowUpDate.isNotBlank()) {
            yPos = (yPos + 30f).coerceAtLeast(340f)
            val boxWidth = (pageWidth - (2 * MARGIN)) / 2f
            paint.style = Paint.Style.STROKE
            canvas.drawRect(MARGIN, yPos, MARGIN + boxWidth, yPos + 40f, paint)
            
            paint.style = Paint.Style.FILL
            paint.textSize = 9f
            paint.typeface = Typeface.create("serif", Typeface.BOLD)
            canvas.drawText("NEXT FOLLOW-UP ON:", MARGIN + 10f, yPos + 15f, paint)
            
            paint.textSize = 10f
            canvas.drawText(consultation.nextFollowUpDate, MARGIN + 10f, yPos + 32f, paint)
        } else {
            yPos = 340f
        }

        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        val boxWidth = (pageWidth - (2 * MARGIN)) / 2f
        val sigAreaLeft = MARGIN + boxWidth + 20f
        val sigCenterX = (sigAreaLeft + (pageWidth - MARGIN)) / 2f
        
        canvas.drawLine(sigAreaLeft + 20f, yPos + 35f, pageWidth - MARGIN - 20f, yPos + 35f, paint)
        paint.textSize = 9f
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        canvas.drawText("Signature / Stamp", sigCenterX, yPos + 48f, paint)

        yPos = 405f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.GRAY
        paint.textSize = 7f
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        canvas.drawText("* Please call before visiting for consultation.", MARGIN, yPos, paint)
    }

    private fun drawReceiptContent(context: Context, canvas: Canvas, patient: Patient, vaccination: Vaccination) {
        val paint = Paint()
        var yPos = 40f
        val pageWidth = PAGE_WIDTH.toFloat()

        yPos = drawClinicHeader(context, canvas, paint, yPos)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        paint.textSize = 20f
        paint.color = Color.rgb(200, 200, 200)
        canvas.drawText("RECEIPT", pageWidth - MARGIN, 55f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 9f
        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        canvas.drawText("No: #VM-${vaccination.id.takeLast(6).uppercase()}", pageWidth - MARGIN, 75f, paint)
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        canvas.drawText("Date: ${vaccination.dateGiven}", pageWidth - MARGIN, 87f, paint)
        
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Time: $timeStr", pageWidth - MARGIN, 99f, paint)
        
        paint.textAlign = Paint.Align.LEFT
        paint.strokeWidth = 1.5f
        canvas.drawLine(MARGIN, yPos, pageWidth - MARGIN, yPos, paint)
        yPos += 20f

        yPos = drawPatientInfoBar(canvas, paint, patient, yPos)
        
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        paint.textSize = 9f
        canvas.drawText("Administered by: Dr. Farogh Hassan (MBBS, DCH, DNB(Paediatrics), PGDDN)", MARGIN, yPos, paint)
        yPos += 18f

        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("Vaccine Description", MARGIN, yPos, paint)
        canvas.drawText("Batch", MARGIN + 300f, yPos, paint) 
        canvas.drawText("Exp.", MARGIN + 400f, yPos, paint) 
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", pageWidth - MARGIN, yPos, paint)
        
        yPos += 8f
        paint.strokeWidth = 1f
        canvas.drawLine(MARGIN, yPos, pageWidth - MARGIN, yPos, paint)
        yPos += 20f
        
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        
        val names = vaccination.vaccineNames
        val batches = vaccination.batchNumbers.ifEmpty { emptyList() }
        val expiries = vaccination.expiryDates.ifEmpty { emptyList() }
        
        val batchX = MARGIN + 300f
        val expX = MARGIN + 400f
        
        for (i in names.indices) {
            val name = names[i]
            val batch = batches.getOrNull(i) ?: ""
            val expiry = expiries.getOrNull(i) ?: ""
            
            val wrappedLines = wrapText(name, (batchX - MARGIN) - 10f, paint)
            
            wrappedLines.forEachIndexed { index, line ->
                canvas.drawText(line, MARGIN, yPos + (index * 12f), paint)
            }
            
            canvas.drawText(batch, batchX, yPos, paint)
            canvas.drawText(expiry, expX, yPos, paint)
            
            yPos += wrappedLines.size.coerceAtLeast(1) * 12f + 6f
        }

        if (vaccination.withFees) {
            canvas.drawText("Consultation Fee", MARGIN, yPos, paint)
            yPos += 18f
        }

        yPos += 4f
        canvas.drawLine(pageWidth - MARGIN - 100f, yPos, pageWidth - MARGIN, yPos, paint)
        yPos += 16f
        
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        paint.textSize = 12f
        val displayTotal = if (vaccination.withFees) vaccination.totalPaid + 400.0 else vaccination.totalPaid
        canvas.drawText("Total Paid: ₹${String.format(Locale.getDefault(), "%.2f", displayTotal)}", pageWidth - MARGIN, yPos, paint)

        yPos += 15f
        paint.textSize = 9f
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        val paymentMode = when {
            vaccination.cashAmount > 0 && vaccination.onlineAmount > 0 -> "Cash & UPI"
            vaccination.cashAmount > 0 -> "Cash"
            vaccination.onlineAmount > 0 -> "UPI"
            else -> "Not Specified"
        }
        canvas.drawText("Mode: $paymentMode", pageWidth - MARGIN, yPos, paint)
        
        yPos = (yPos + 30f).coerceAtLeast(340f) 
        paint.textAlign = Paint.Align.LEFT
        val boxWidth = (pageWidth - (2 * MARGIN)) / 2f
        
        paint.style = Paint.Style.STROKE
        canvas.drawRect(MARGIN, yPos, MARGIN + boxWidth, yPos + 40f, paint)
        
        paint.style = Paint.Style.FILL
        paint.textSize = 9f
        paint.typeface = Typeface.create("serif", Typeface.BOLD)
        canvas.drawText("NEXT VACCINATION DUE ON:", MARGIN + 10f, yPos + 15f, paint)
        
        val recommendedList = vaccination.nxtVaccineNames.filter { it.isNotBlank() }
        val recStr = if (recommendedList.isNotEmpty()) " (${recommendedList.joinToString(", ")})" else ""
        
        var combinedText = vaccination.nextDueDate + recStr
        paint.textSize = 10f
        
        val maxBoxWidth = boxWidth - 20f
        if (paint.measureText(combinedText) > maxBoxWidth) {
            val count = paint.breakText(combinedText, true, maxBoxWidth - paint.measureText("..."), null)
            combinedText = combinedText.substring(0, count) + "..."
        }
        
        canvas.drawText(combinedText, MARGIN + 10f, yPos + 32f, paint)

        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        val sigAreaLeft = MARGIN + boxWidth + 20f
        val sigCenterX = (sigAreaLeft + (pageWidth - MARGIN)) / 2f
        
        canvas.drawLine(sigAreaLeft + 20f, yPos + 35f, pageWidth - MARGIN - 20f, yPos + 35f, paint)
        paint.textSize = 9f
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        canvas.drawText("Signature / Stamp", sigCenterX, yPos + 48f, paint)

        yPos = 405f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.GRAY
        paint.textSize = 7f
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        canvas.drawText("* Please call before visiting for vaccination.", MARGIN, yPos, paint)
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
                while (paint.measureText(currentLine.toString()) > maxWidth && currentLine.length > 1) {
                    val sub = currentLine.substring(0, currentLine.length - 1)
                    lines.add(sub)
                    currentLine = StringBuilder(currentLine.substring(currentLine.length - 1))
                }
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}
