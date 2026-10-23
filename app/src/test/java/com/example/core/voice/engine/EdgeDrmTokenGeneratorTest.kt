package com.example.core.voice.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeDrmTokenGeneratorTest {

  @Test
  fun testSecMsGecKnownVectors() {
    val token1 = EdgeDrmTokenGenerator.generateSecMsGec(1700000000.0)
    assertEquals("2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2", token1)

    val token2 = EdgeDrmTokenGenerator.generateSecMsGec(1730000000.0)
    assertEquals("50065ABF67B90F95-49BDAF24D28ECEB38DAEEC84DEAA71E91F92945EA8A47921C58FB4F8F79A4F5E", token2)
  }
}
