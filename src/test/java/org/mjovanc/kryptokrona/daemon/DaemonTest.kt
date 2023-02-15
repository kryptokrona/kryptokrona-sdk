package org.mjovanc.kryptokrona.daemon;

import inet.ipaddr.HostName
import org.junit.jupiter.api.Test
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DaemonTest {

    private var correctDaemonUrl: String = "swepool.org:11898"
    private var incorrectDaemonUrl: String = "mjovanc.kryptokrona.se:11898"

    @Test
    fun `can initialize daemon`() {
        val daemon = DaemonImpl(HostName(correctDaemonUrl), false)
        daemon.init()

        assertEquals(true, daemon.isConnected)
    }

    @Test
    fun `can not initialize daemon with bad dns resolution`() {
        assertFailsWith<UnknownHostException> {
            val daemon = DaemonImpl(HostName(incorrectDaemonUrl), false)
            daemon.init()
        }
    }

    @Test
    fun `node status is OK when getting fee info`() {
        val daemon = DaemonImpl(HostName(correctDaemonUrl), false)
        daemon.init()

        daemon.updateFeeInfo().subscribe {
            val status = daemon.nodeFee.status

            assertEquals("OK", status)
        }
    }
}