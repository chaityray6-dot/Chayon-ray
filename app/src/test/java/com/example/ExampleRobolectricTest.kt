package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.pdf.ScanFilterMode
import com.example.data.pdf.ScannerImageProcessor
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
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Smart PDF Reader", appName)
  }

  @Test
  fun `cropAndRotateBitmap crops normalized region and rotates`() {
    val src = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
    src.eraseColor(Color.WHITE)

    val cropped = ScannerImageProcessor.cropAndRotateBitmap(
      source = src,
      leftNorm = 0.25f,
      topNorm = 0.0f,
      rightNorm = 0.75f,
      bottomNorm = 0.5f,
      rotationDegrees = 90
    )

    // 100x200 rotated 90 degrees -> 200x100; 50% width x 50% height -> 100x50
    assertEquals(100, cropped.width)
    assertEquals(50, cropped.height)
    cropped.recycle()
    src.recycle()
  }

  @Test
  fun `applySharpenAndClarity preserves dimensions and enhances edge contrast`() {
    val src = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    for (y in 0 until 24) {
      for (x in 0 until 24) {
        val v = if (x in 8..15 && y in 8..15) 90 else 210
        src.setPixel(x, y, Color.rgb(v, v, v))
      }
    }

    val sharpened = ScannerImageProcessor.applySharpenAndClarity(src, sharpenStrength = 1.0f, denoise = true)
    assertEquals(24, sharpened.width)
    assertEquals(24, sharpened.height)

    val enhanced = ScannerImageProcessor.applyEnhancement(
      source = src,
      filter = ScanFilterMode.CLEAR_TEXT,
      brightness = 0f,
      contrast = 1.15f,
      sharpen = 0.9f
    )
    assertTrue(enhanced.width == 24 && enhanced.height == 24)

    sharpened.recycle()
    enhanced.recycle()
    src.recycle()
  }
}
