package th.co.lotto

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflinePolicyTest {
    @Test
    fun androidSystemBackupRemainsDisabled() {
        val context = RuntimeEnvironment.getApplication() as Context
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertFalse(
            "Android cloud/device backup must remain disabled",
            applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        )
    }
}
