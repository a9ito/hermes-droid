package com.a9ito.hermesagent.ui

import androidx.annotation.StringRes
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.core.ErrorKind

/** Maps a domain [ErrorKind] to a user-facing string resource. */
@StringRes
fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.NO_CONNECTION -> R.string.error_no_connection
    ErrorKind.AUTH -> R.string.error_auth
    ErrorKind.NETWORK -> R.string.error_network
    ErrorKind.TIMEOUT -> R.string.error_timeout
    ErrorKind.SERVER_ERROR -> R.string.error_server
    ErrorKind.UNEXPECTED -> R.string.error_unexpected
}
