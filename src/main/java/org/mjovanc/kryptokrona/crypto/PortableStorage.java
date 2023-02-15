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

package org.mjovanc.kryptokrona.crypto;

import java.nio.Buffer;
import java.util.List;
import java.util.Objects;

/**
 * PortableStorage.java
 *
 * @author Marcus Cvjeticanin (@mjovanc)
 */
public class PortableStorage {

	protected int version;

	protected int signatureA;

	protected int signatureB;

	protected List<PortableStorageEntry> entries;

	public PortableStorage() {
		this.version = PortableStorageConstants.VERSION.getCode();
		this.signatureA = PortableStorageConstants.SIGNATURE_A.getCode();
		this.signatureB = PortableStorageConstants.SIGNATURE_B.getCode();
	}

	private boolean exists(String name) {
		for (PortableStorageEntry entry : entries) {
			if (Objects.equals(entry.getName(), name)) {
				return true;
			}
		}

		return false;
	}

	public Buffer toBuffer(boolean skipHeader) {
		return null;
	}

	private List<PortableStorageEntry> blobToEntries() {
		return null;
	}

	private Buffer entriesToBuffer(List<PortableStorageEntry> entries) {
		return null;
	}


}
