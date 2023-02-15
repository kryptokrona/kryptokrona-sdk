// Copyright (c) 2022-2023, The Kryptokrona Developers
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

package org.mjovanc.kryptokrona.service;

import io.reactivex.rxjava3.core.Observable;
import lombok.Getter;
import lombok.Setter;
import org.mjovanc.kryptokrona.block.Block;
import org.mjovanc.kryptokrona.block.TopBlock;
import org.mjovanc.kryptokrona.crypto.KeyPair;
import org.mjovanc.kryptokrona.daemon.DaemonImpl;
import org.mjovanc.kryptokrona.exception.node.NodeDeadException;
import org.mjovanc.kryptokrona.model.http.WalletSyncData;
import org.mjovanc.kryptokrona.transaction.TransactionData;
import org.mjovanc.kryptokrona.transaction.TransactionInputImpl;
import org.mjovanc.kryptokrona.transaction.TransactionRaw;
import org.mjovanc.kryptokrona.transaction.TransactionRawCoinbase;
import org.mjovanc.kryptokrona.wallet.SubWallets;
import org.mjovanc.kryptokrona.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WalletSynchronizerService.java
 *
 * @author Marcus Cvjeticanin (@mjovanc)
 */
@Getter
@Setter
public class WalletSynchronizerService {

	private DaemonImpl daemon;

	private long startTimestamp;

	private long startHeight;

	private String privateViewKey;

	private SynchronizationStatus synchronizationStatus;

	private SubWallets subWallets;

	private boolean fetchingBlocks;

	private List<Block> storedBlocks;

	private Map<String, Long> cancelledTransactionsFailCount;

	private Instant lastDownloadedBlocks;

	private static final Logger logger = LoggerFactory.getLogger(WalletSynchronizerService.class);

	public WalletSynchronizerService(
		SubWallets subWallets,
		long startTimestamp,
		long startHeight,
		String privateViewKey
	) {
		this.subWallets = subWallets;
		this.startTimestamp = startTimestamp;
		this.startHeight = startHeight;
		this.privateViewKey = privateViewKey;
		this.synchronizationStatus = new SynchronizationStatus();
		this.lastDownloadedBlocks = Instant.now();
	}

	public void initAfterLoad(
		SubWallets subWallets,
		DaemonImpl daemon
	) {
		this.subWallets = subWallets;
		this.daemon = daemon;
		this.storedBlocks = new ArrayList<>();
		this.cancelledTransactionsFailCount = new HashMap<>();
		this.lastDownloadedBlocks = Instant.now();
	}

	public TransactionData processBlock(Block block, Map<String, TransactionInputImpl> ourInputs) {
		var txData = new TransactionData();

		// TODO: implement if statement and logic inside when crypto library is available in java

		txData.setInputsToAdd(ourInputs);

		return txData;
	}

	/**
	 * Process transaction outputs of the given block. No external dependencies,
	 * lets us easily swap out with a C++ replacement for SPEEEED
	 *
	 * @param block                       The block to use
	 * @param privateViewKey              The private view key
	 * @param allSpendKeys                List of spend keys
	 * @param isViewWallet                If its a view wallet
	 * @param processCoinbaseTransactions If we should process coinbase transactions
	 * @return Returns a map of transaction outputs
	 */
	public Observable<Map<String, List<TransactionInputImpl>>> processBlockOutputs(
		Block block,
		String privateViewKey,
		List<KeyPair> allSpendKeys,
		boolean isViewWallet,
		boolean processCoinbaseTransactions
	) {
		var inputs = new HashMap<String, List<TransactionInputImpl>>();

		if (processCoinbaseTransactions && (block.getTransactionRawCoinbase() != null)) {
			processTransactionOutputs(block.getTransactionRawCoinbase(), block.getBlockHeight())
				.subscribe(outputs -> {
					inputs.values().addAll(outputs.values());
				});
		}

		for (var tx : block.getTransactions()) {
			processTransactionOutputs(tx, block.getBlockHeight()).subscribe(list -> {
				inputs.values().addAll(list.values());
			});
		}

		return Observable.just(inputs);
	}

	/**
	 * Get the height of the sync process.
	 *
	 * @return long : last known block height
	 */
	public long getHeight() {
		return 0;
		//TODO: code below will result in null exception
		// return synchronizationStatus.getLastKnownBlockHeight();
	}

	public Observable<Void> reset(long scanHeight, long scanTimestamp) {
		return null;
	}

	public Observable<Void> rewind(long scanHeight) {
		return null;
	}

	public Observable<List<String>> findCancelledTransactions(List<String> transactionHashes) {
		return null;
	}

	/**
	 * Retrieve blockCount blocks from the internal store. Does not remove
	 * them.
	 *
	 * @return Observable
	 */
	public Observable<HashMap<Boolean, List<Block>>> fetchBlocks() throws NodeDeadException {
		var map = new HashMap<Boolean, List<Block>>();

		// fetch more blocks if we haven't got any downloaded yet
		if (storedBlocks != null) {

			if (storedBlocks.size() == 0) {
				if (!fetchingBlocks) {
					logger.info("No blocks stored, attempting to fetch more.");
				}

				var blocks = downloadBlocks().blockingSingle();

				var successOrBusy = blocks.keySet().iterator().next();
				var shouldSleep = blocks.values().iterator().next();

				// not in the middle of fetching blocks.
				if (!successOrBusy) {
					// seconds since we last got a block
					var diff = (Instant.now().toEpochMilli() - lastDownloadedBlocks.toEpochMilli()) / 1000;

					if (diff > Config.MAX_LAST_FETCHED_BLOCK_INTERVAL) {
						throw new NodeDeadException();
					}
				} else {
					lastDownloadedBlocks = Instant.now();
				}

				map.put(shouldSleep, storedBlocks.subList(0, (int) Config.BLOCKS_PER_TICK));
			}
		}

		return Observable.just(map);
	}

	public void dropBlock(long blockHeight, String blockHash) {

	}

	public List<String> getBlockCheckpoints() {
		return null;
	}

	public List<String> getRecentBlockHashes() {
		return null;
	}

	public List<String> getStoredBlockCheckpoints() {
		return null;
	}

	private boolean shouldFetchMoreBlocks() {
		return false;
	}

	public ArrayList<String> getWalletSyncDataHashes() {
		return null;
	}

	private Observable<Map<Boolean, Boolean>> downloadBlocks() {
		/* Middle of fetching blocks, wait for previous request to complete.
		 * Don't need to sleep. */
		if (fetchingBlocks) {
			return Observable.just(Map.of(true, false));
		}

		fetchingBlocks = true;

		var localDaemonBlockCount = daemon.getLocalDaemonBlockCount();
		var walletBlockCount = getHeight();

		if (localDaemonBlockCount < walletBlockCount) {
			this.fetchingBlocks = false;
			return Observable.just(Map.of(true, true));
		}

		/* Get the checkpoints of the blocks we've got stored, so we can fetch
           later ones. Also use the checkpoints of the previously processed
           ones, in case we don't have any blocks yet. */
		var blockCheckpoints = getWalletSyncDataHashes();

		List<Block> blocks = null;
		var topBlock = new TopBlock();
		var walletSyncData = new WalletSyncData(blockCheckpoints, startHeight, startTimestamp);

		try {
			var data = daemon.getWalletSyncData(walletSyncData).blockingSingle();
			blocks = data.keySet().iterator().next();
			topBlock = data.values().iterator().next();
		} catch (Exception e) {
			logger.error("Failed to get blocks from daemon: ", e);

			//TODO: finishedFunc?

			fetchingBlocks = false;

			return Observable.just(Map.of(false, true));
		}

		// TODO: implement the rest here

		return Observable.empty();
	}

	private Observable<Map<String, List<TransactionInputImpl>>> processTransactionOutputs(
		TransactionRawCoinbase rawTx,
		long blockHeight
	) {
		return null;
	}

	private Observable<List<Map<String, List<TransactionInputImpl>>>> processCoinbaseTransaction(
		Block block,
		List<Map<String, TransactionInputImpl>> ourInputs
	) {
		return null;
	}

	//TODO should return another type
	private Observable<Void> processTransaction(
		Block block,
		List<Map<String, TransactionInputImpl>> ourInputs,
		TransactionRaw rawTx
	) {
		return null;
	}
}