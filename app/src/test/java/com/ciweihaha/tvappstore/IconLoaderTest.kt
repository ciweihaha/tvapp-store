package com.ciweihaha.tvappstore

import com.ciweihaha.tvappstore.util.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Test

/** 图标采样倍率测试：大图按比例缩小，防 OOM。 */
class IconLoaderTest {

    @Test
    fun sampleSizeDownscalesLargeImages() {
        // 1024px 的图，目标 256px：采样 4 倍
        assertEquals(4, IconLoader.calculateSampleSize(1024, 1024, 256))
        // 512x256：最长边 512，采样 2 倍
        assertEquals(2, IconLoader.calculateSampleSize(512, 256, 256))
        // 300x300：采样 2 倍后 150px
        assertEquals(2, IconLoader.calculateSampleSize(300, 300, 256))
    }

    @Test
    fun sampleSizeKeepsSmallImages() {
        assertEquals(1, IconLoader.calculateSampleSize(100, 100, 256))
        assertEquals(1, IconLoader.calculateSampleSize(256, 256, 256))
    }

    @Test
    fun sampleSizeHandlesInvalidInput() {
        assertEquals(1, IconLoader.calculateSampleSize(0, 0, 256))
        assertEquals(1, IconLoader.calculateSampleSize(-1, 100, 256))
        assertEquals(1, IconLoader.calculateSampleSize(1024, 1024, 0))
    }
}
