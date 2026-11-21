package com.flipfate.lbsroc.entry

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.flipfate.lbsroc.R

internal fun Activity.openBrowser(pageUrl: String) {
    val browserIntent = Intent(this, BrowserActivity::class.java).apply {
        putExtra(BrowserActivity.EXTRA_PAGE_URL, pageUrl)
    }
    startActivity(browserIntent)
    applyPortalFadeTransition(this, closing = false)
    finish()
    applyPortalFadeTransition(this, closing = true)
}

@Suppress("DEPRECATION")
private fun applyPortalFadeTransition(activity: Activity, closing: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val type = if (closing) {
            Activity.OVERRIDE_TRANSITION_CLOSE
        } else {
            Activity.OVERRIDE_TRANSITION_OPEN
        }
        activity.overrideActivityTransition(
            type,
            R.anim.portal_fade_in,
            R.anim.portal_fade_out
        )
    } else {
        activity.overridePendingTransition(R.anim.portal_fade_in, R.anim.portal_fade_out)
    }
}
