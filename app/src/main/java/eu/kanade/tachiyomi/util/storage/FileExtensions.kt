package eu.kanade.tachiyomi.util.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import eu.kanade.tachiyomi.BuildConfig
import java.io.File

/**
 * Returns the uri of a file
 *
 * @param context context of application
 */
fun File.getUriCompat(context: Context): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", this)
    } else {
        Uri.fromFile(this)
    }

/**
 * Copies this file to the target [destination] and sets it read-only.
 *
 * @param destination The destination file.
 * @param overwrite Whether to overwrite the destination if it already exists.
 * @return The destination file.
 */
fun File.copyAndSetReadOnlyTo(
    destination: File,
    overwrite: Boolean = true,
): File {
    if (overwrite) {
        destination.delete()
    }
    this.copyTo(destination, overwrite = overwrite)
    destination.setReadOnly()
    return destination
}
