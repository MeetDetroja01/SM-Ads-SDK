package com.smappsstudio.ads

import android.content.Context

class SMAppConfig(
    val context: Context,
    val isDebug: Boolean
) {
    var adjustToken: String = ""
    var facebookClientToken: String = ""
    var tiktokEventToken: String = ""
    
    // Facebook application ID (optional, can also be read from strings.xml)
    var facebookAppId: String = ""
}
