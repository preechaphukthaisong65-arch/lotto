package th.co.lotto

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflinePolicyTest {
    @Test
    fun appAllowsPublicGloRetrievalButDisablesAndroidSystemBackup() {
        val context = RuntimeEnvironment.getApplication() as Context
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()

        assertTrue(
            "Lotto needs internet only to retrieve public GLO results",
            Manifest.permission.INTERNET in requested
        )
        assertFalse(
            "Android cloud/device backup must remain disabled",
            applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        )
    }
}
