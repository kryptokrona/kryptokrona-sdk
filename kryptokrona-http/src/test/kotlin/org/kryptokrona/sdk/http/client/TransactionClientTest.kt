package org.kryptokrona.sdk.http.client

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.kryptokrona.sdk.core.node.Node

class TransactionClientTest {

    private val node = Node("privacymine.net", 11898, false)

    private val client = TransactionClient(node)

    @Test
    fun `can get transactions` () = runTest {
        val data = client.getTransactions()
        assertNotNull(data)
    }

    @Test
    fun `can get transaction details by hashes` () = runTest {
        val data = client.getTransactionDetailsByHashes()
        assertNotNull(data)
    }

    @Test
    fun `can get transaction hashes by payment id` () = runTest {
        val data = client.getTransactionHashesByPaymentId()
        assertNotNull(data)
    }

    @Test
    fun `can get transaction status` () = runTest {
        val data = client.getTransactionsStatus()
        assertNotNull(data)
    }


}