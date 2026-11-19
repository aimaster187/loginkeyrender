package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pranav.guard.PranavGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches PRANAV branding`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PRANAV", appName)
  }

  @Test
  fun `pranav gate device id is stable and formatted`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val id1 = PranavGate.getDeviceId(context)
    val id2 = PranavGate.getDeviceId(context)
    assertEquals(id1, id2)
    assertTrue(id1.startsWith("PRNV-"))
  }

  @Test
  fun `pranav gate persists license key`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    PranavGate.saveLicenseKey(context, "PRANAV-1234-5678-90AB")
    assertEquals("PRANAV-1234-5678-90AB", PranavGate.getSavedLicenseKey(context))
  }
}
