// Copyright (c) 2022-2023, The Kryptokrona Developers
//
// Written by Marcus Cvjeticanin
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification, are
// permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice, this list
//    of conditions and the following disclaimer in the documentation and/or other
//    materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its contributors may be
//    used to endorse or promote products derived from this software without specific
//    prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
// THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
// PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
// THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.kryptokrona.http.client

import io.ktor.client.call.*
import org.kryptokrona.core.node.Node
import org.kryptokrona.http.common.get
import org.kryptokrona.http.model.GlobalIndexesForRange
import org.kryptokrona.http.model.OIndexes
import org.slf4j.LoggerFactory

class IndexesClient(private val node: Node) {

    private val logger = LoggerFactory.getLogger("IndexesClient")

    /**
     * Get O Indexes
     *
     * @return OIndexes
     */
    suspend fun getOIndexes(): OIndexes? {
        try {
            node.ssl.let {
                if (it) {
                    return get("https://${node.hostName}:${node.port}/get_o_indexes").body()
                } else {
                    return get("http://${node.hostName}:${node.port}/get_o_indexes").body()
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting O Indexes", e)
        }

        return null
    }

    /**
     * Get global indexes for range
     *
     * @return GlobalIndexesForRange
     */
    suspend fun getGlobalIndexesForRange(): GlobalIndexesForRange? {
        try {
            node.ssl.let {
                if (it) {
                    return get("https://${node.hostName}:${node.port}/get_global_indexes_for_range").body()
                } else {
                    return get("http://${node.hostName}:${node.port}/get_global_indexes_for_range").body()
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting global indexes for range", e)
        }

        return null
    }

}