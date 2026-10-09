package com.a9ito.hermesagent.ui.common

import android.content.Context
import android.media.ExifInterface
import java.io.File

/**
 * Best-effort removal of location (and other identifying) EXIF metadata from a
 * JPEG the user is about to attach, so a photo's GPS coordinates are not
 * forwarded to the agent (and, if the instance is compromised, to an attacker).
 *
 * Android-only (platform [ExifInterface] writes through a seekable file), so it
 * is verified by CI compile + on-device, not the JVM harness. Scope and policy:
 *  - Only `image/jpeg` is processed: that is where consumer-camera GPS lives and
 *    is the format the platform ExifInterface reliably rewrites. Other formats
 *    pass through byte-identical.
 *  - If no location tag is present, the original bytes are returned untouched
 *    (no needless re-save), so a screenshot or already-clean photo is unchanged.
 *  - On any failure the original bytes are returned (best-effort hardening, not a
 *    hard gate): the user deliberately chose to send this image to their own
 *    instance. The residual is documented in SECURITY-AUDIT.md.
 *
 * The scratch file lives in [Context.getCacheDir] (app-private storage, not
 * world-readable) and is always deleted in `finally`.
 */
object ExifScrubber {

    fun stripLocation(context: Context, bytes: ByteArray, mimeType: String): ByteArray {
        if (mimeType != "image/jpeg" && mimeType != "image/jpg") return bytes
        var tmp: File? = null
        return try {
            tmp = File.createTempFile("attach_", ".jpg", context.cacheDir)
            tmp.writeBytes(bytes)
            val exif = ExifInterface(tmp.absolutePath)
            var changed = false
            for (tag in LOCATION_TAGS) {
                if (exif.getAttribute(tag) != null) {
                    exif.setAttribute(tag, null)
                    changed = true
                }
            }
            if (!changed) return bytes
            exif.saveAttributes()
            tmp.readBytes()
        } catch (_: Throwable) {
            bytes
        } finally {
            tmp?.delete()
        }
    }

    // GPS and camera-identifying tags that carry or narrow location. Limited to
    // constants present since API 24 (minSdk is 26), so this compiles cleanly.
    private val LOCATION_TAGS: List<String> = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
    )
}
