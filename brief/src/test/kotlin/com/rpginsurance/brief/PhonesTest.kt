package com.rpginsurance.brief

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhonesTest {
    @Test fun whatsapp() {
        assertEquals("919876543210", Phones.whatsapp("98765 43210"))
        assertEquals("919876543210", Phones.whatsapp("098765-43210"))
        assertEquals("919876543210", Phones.whatsapp("+91 98765 43210"))
        assertEquals("919876543210", Phones.whatsapp("919876543210"))
        assertEquals("447700900123", Phones.whatsapp("+44 7700 900123"))
        assertEquals("447700900123", Phones.whatsapp("0044 7700 900123"))
        assertNull(Phones.whatsapp("  "))
        assertNull(Phones.whatsapp("n/a"))
    }

    @Test fun dialable() {
        assertEquals("+919876543210", Phones.dialable("+91 (98765) 43210"))
        assertEquals("9876543210", Phones.dialable("98765 43210"))
    }
}
