package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class DoorSelectionTest {
    @Test fun defaultDoorIsEntryAndSearchReturnsSelectableDoor() {
        val doors = GuardianSessionRepository.doors
        val selected = doors.firstOrNull { it.id == GuardianSessionRepository.DOOR_ENTRY } ?: doors.first()
        assertEquals("Entrada", selected.name)
        val filtered = doors.filter { it.name.contains("baño", ignoreCase = true) || it.id.contains("bano", ignoreCase = true) }
        assertEquals(GuardianSessionRepository.DOOR_BATH, filtered.single().id)
    }
    @Test fun rolesAreStableValues() {
        assertEquals("tutor", GuardianSessionRepository.ROLE_TUTOR)
        assertEquals("admin", GuardianSessionRepository.ROLE_ADMIN)
    }
}
