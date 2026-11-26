package my.edu.unikl.tapsafe.victim

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stores non-sensitive test events locally. No touch coordinates or user input are recorded. */
object ResultStore {
    private const val FILE_NAME = "tapsafe-lab-results.csv"

    fun record(context: Context, event: String, detail: String) {
        val safeEvent = event.replace(',', ';').replace('\n', ' ')
        val safeDetail = detail.replace(',', ';').replace('\n', ' ')
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        context.openFileOutput(FILE_NAME, Context.MODE_APPEND).bufferedWriter().use { writer ->
            if (File(context.filesDir, FILE_NAME).length() == 0L) {
                writer.appendLine("timestamp,event,detail")
            }
            writer.appendLine("$timestamp,$safeEvent,$safeDetail")
        }
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else "No test events recorded yet."
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
