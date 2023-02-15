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

package org.mjovanc.kryptokrona.wallet;

import io.reactivex.rxjava3.core.Observable;
import lombok.Getter;
import lombok.Setter;
import org.mjovanc.kryptokrona.exception.wallet.*;
import org.mjovanc.kryptokrona.exception.wallet.*;
import org.mjovanc.kryptokrona.model.util.TxInputAndOwner;
import org.mjovanc.kryptokrona.model.util.UnconfirmedInput;
import org.mjovanc.kryptokrona.transaction.Transaction;
import org.mjovanc.kryptokrona.transaction.TransactionInput;
import org.mjovanc.kryptokrona.util.CryptoUtils;
import org.mjovanc.kryptokrona.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SubWallets.java
 *
 * @author Marcus Cvjeticanin (@mjovanc)
 */
@Getter
@Setter
public class SubWallets {

	private final Address address;

	private final long scanHeight;

	private final boolean newWallet;

	private final String privateSpendKey;

	/**
	 * Whether the wallet is a view only wallet (cannot send transactions,
	 * only can view).
	 */
	private boolean isViewWallet;

	/**
	 * The public spend keys this wallet contains. Used for verifying if a
	 * transaction is ours.
	 */
	private List<String> publicSpendKeys;

	/**
	 * Mapping of public spend key to sub wallet.
	 */
	private Map<String, SubWallet> subWallets;

	/**
	 * Our transactions.
	 */
	private List<Transaction> transactions;

	/**
	 * Transactions we sent, but haven't been confirmed yet.
	 */
	private List<Transaction> lockedTransactions;

	/**
	 * The shared private view key.
	 */
	private String privateViewKey;

	/**
	 * A mapping of transaction hashes, to transaction private keys.
	 */
	private Map<String, String> transactionPrivateKeys;

	/**
	 * A mapping of key images to the sub wallet public spend key that owns them.
	 */
	private Map<String, String> keyImageOwners;

	private static final Logger logger = LoggerFactory.getLogger(SubWallets.class);

	public SubWallets(Address address, long scanHeight, boolean newWallet, String privateViewKey, String privateSpendKey) {
		this.address = address;
		this.scanHeight = scanHeight;
		this.newWallet = newWallet;
		this.privateViewKey = privateViewKey;
		this.privateSpendKey = privateSpendKey;
		this.lockedTransactions = new ArrayList<>();
	}

	public static Observable<SubWallets> init(
		String address, long scanHeight, boolean newWallet, String privateViewKey, String privateSpendKey
	) throws WalletAddressChecksumMismatchException {
		var timestamp = 0L;

		if (newWallet) {
			timestamp = CryptoUtils.getCurrentTimestampAdjusted(Config.BLOCK_TARGET_TIME);
		}

		long finalTimestamp = timestamp;
		Address.fromAddress(address, Config.ADDRESS_PREFIX)
			.subscribe(decodedAddress -> {
				var publicSpendKeys = new ArrayList<String>();

				publicSpendKeys.add(decodedAddress.getSpendKeys().getPublicKey());

				var subWallet = new SubWallet(address, scanHeight, finalTimestamp, decodedAddress.getSpendKeys(), true);

				var subWallets = new HashMap<String, SubWallet>();
				subWallets.put(decodedAddress.getSpendKeys().getPublicKey(), subWallet);

				//TODO: this method is not done
				// return Observable.just();
			});

		return Observable.empty();
	}

	public void initKeyImageMap() {
		for (var subWallet : subWallets.values()) {
			for (var keyImage : subWallet.getKeyImages()) {
				keyImageOwners.put(keyImage, subWallet.getSpendKeys().getPublicKey());
			}
		}
	}

	public void pruneSpentInputs(long pruneHeight) {
		for (var subWallet : subWallets.values()) {
			subWallet.pruneSpentInputs(pruneHeight);
		}
	}

	public void reset(long scanHeight, long scanTimestamp) {
		transactions = new ArrayList<>();
		lockedTransactions = new ArrayList<>();
		transactionPrivateKeys = new HashMap<>();
		keyImageOwners = new HashMap<>();

		for (var subWallet : subWallets.values()) {
			subWallet.reset(scanHeight, scanTimestamp);
		}
	}

	public void rewind(long scanHeight) {
		lockedTransactions = new ArrayList<>();
		removeForkedTransactions(scanHeight);
	}

	/**
	 * Get the private spend key for the given public spend key, if it exists.
	 *
	 * @param publicSpendKey The public spend key
	 * @return Returns the private spend key
	 */
	public String getPrivateSpendKey(String publicSpendKey) throws WalletAddressNotInWalletException {
		var subWallet = getSubWalletByPublicSpendKey(publicSpendKey);

		if (subWallet == null) {
			throw new WalletAddressNotInWalletException();
		}

		return subWallet.getSpendKeys().getPrivateKey();
	}

	/**
	 * Gets the 'primary' sub wallet.
	 *
	 * @return Returns the sub wallet object
	 */
	public SubWallet getPrimarySubWallet() throws WalletSubWalletNoPrimaryAddressException {
		for (var subWallet : subWallets.values()) {
			if (subWallet.isPrimaryAddress()) {
				return subWallet;
			}
		}

		throw new WalletSubWalletNoPrimaryAddressException();
	}

	/**
	 * Gets the primary address of the wallet.
	 *
	 * @return Returns the primary address of the wallet
	 */
	public String getPrimaryAddress() throws WalletSubWalletNoPrimaryAddressException {
		return getPrimarySubWallet().getAddress();
	}

	/**
	 * Gets the private spend key of the primary sub wallet
	 *
	 * @return Returns the primary private spend key
	 */
	public String getPrimaryPrivateSpendKey() throws WalletSubWalletNoPrimaryAddressException {
		return getPrimarySubWallet().getSpendKeys().getPrivateKey();
	}

	/**
	 * Get the hashes of the locked transactions (ones we've sent but not
	 * confirmed).
	 *
	 * @return Returns the locked transaction hashes
	 */
	public List<String> getLockedTransactionHashes() {
		return lockedTransactions
			.stream()
			.map(Transaction::getHash)
			.collect(Collectors.toList());
	}

	/**
	 * Add this transaction to the container. If the transaction was previously
	 * sent by us, remove it from the locked container
	 *
	 * @param transaction The transaction to be added
	 */
	public void addTransaction(Transaction transaction) {
		logger.trace("Transaction " + transaction.getHash());

		/* Remove this transaction from the locked data structure, if we had
           added it previously as an outgoing tx */
		lockedTransactions.removeIf(t -> t.getHash().equals(transaction.getHash()));

		// check if transaction is already in list of transactions
		var transactionInTransaction = transactions.stream()
			.filter(t -> t.getHash().equals(transaction.getHash()))
			.findAny();

		if (transactionInTransaction.isPresent()) {
			logger.debug("Already seen transaction " + transaction.getHash() + ", ignoring.");
		}

		transactions.add(transaction);
	}

	/**
	 * Adds a transaction we sent to the locked transactions container
	 *
	 * @param transaction The transaction to be added
	 */
	public void addUnconfirmedTransaction(Transaction transaction) {
		logger.trace("Unconfirmed transaction " + transaction.getHash());

		var transactionInLockedTransaction = lockedTransactions.stream()
			.filter(t -> t.getHash().equals(transaction.getHash()))
			.findAny();

		if (transactionInLockedTransaction.isPresent()) {
			logger.debug("Already seen unconfirmed transaction " + transaction.getHash() + ", ignoring.");
		}

		lockedTransactions.add(transaction);
	}

	/**
	 * Store the transaction input in the corresponding sub wallet.
	 *
	 * @param publicSpendKey   The public spend key of the sub wallet to add this input to
	 * @param transactionInput The transaction input to store
	 */
	public void storeTransactionInput(String publicSpendKey, TransactionInput transactionInput) throws WalletSubNotFoundException {
		var subWallet = getSubWalletByPublicSpendKey(publicSpendKey);

		if (subWallet == null) {
			throw new WalletSubNotFoundException();
		}

		logger.trace("Input key image " + transactionInput.getKeyImage());

		if (!isViewWallet) {
			keyImageOwners.put(transactionInput.getKeyImage(), publicSpendKey);
		}

		subWallet.storeTransactionInput(transactionInput, isViewWallet);
	}

	/**
	 * Marks an input as spent by us, no longer part of balance or available
	 * for spending. Input is identified by keyImage (unique).
	 *
	 * @param publicSpendKey The public spend key of the sub wallet to mark the corresponding input spent in
	 * @param keyImage       The key image to use
	 * @param spendHeight    The height the input was spent at
	 */
	public void markInputAsSpent(String publicSpendKey, String keyImage, long spendHeight) throws WalletSubNotFoundException {
		var subWallet = getSubWalletByPublicSpendKey(publicSpendKey);

		if (subWallet == null) {
			throw new WalletSubNotFoundException();
		}

		subWallet.markInputAsSpent(keyImage, spendHeight);
	}

	public void markInputAsLocked(String publicSpendKey, String keyImage, String transactionHash) throws WalletSubNotFoundException {
		var subWallet = getSubWalletByPublicSpendKey(publicSpendKey);

		if (subWallet == null) {
			throw new WalletSubNotFoundException();
		}

		subWallet.markInputAsLocked(keyImage, transactionHash);
	}

	/**
	 * Remove a transaction that we sent by didn't get included in a block and
	 * returned to us. Removes the correspoding inputs, too.
	 *
	 * @param transactionHash The transaction hash of the transaction to remove
	 */
	public void removeCancelledTransaction(String transactionHash) {
		// remove the transaction if it was locked
		lockedTransactions.removeIf(t -> t.getHash().equals(transactionHash));

		// remove the corresponding inputs
		for (var subWallet : subWallets.values()) {
			subWallet.removeCancelledTransaction(transactionHash);
		}
	}

	/**
	 * Remove transactions which occured in a forked block. If they got added
	 * in another block, we'll add them back again then.
	 *
	 * @param forkHeight Remove transactions on a specific fork block
	 */
	public void removeForkedTransactions(long forkHeight) {
		transactions.removeIf(t -> t.getBlockHeight() >= forkHeight);

		var keyImagesToRemove = new ArrayList<String>();

		for (var subWallet : subWallets.values()) {
			keyImagesToRemove.addAll(subWallet.removeForkedTransactions(forkHeight));
		}

		if (!isViewWallet) {
			for (var keyImage : keyImagesToRemove) {
				keyImageOwners.keySet().removeIf(ki -> ki.equals(keyImage));
			}
		}
	}

	/**
	 * Convert a timestamp to a block height. Block heights are more dependable
	 * than timestamps, which sometimes get treated a little funkily by the
	 * daemon.
	 *
	 * @param timestamp The timestamp to convert
	 * @param height    The block height to use
	 */
	public void convertSyncTimestampToHeight(long timestamp, long height) {
		for (var subWallet : subWallets.values()) {
			subWallet.convertSyncTimestampToHeight(timestamp, height);
		}
	}

	public boolean haveSpendableInput(TransactionInput transactionInput, long height) {
		for (var subWallet : subWallets.values()) {
			if (subWallet.haveSpendableInput(transactionInput, height)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Get the owner (i.e., the public spend key of the sub wallet) of this
	 * keyImage.
	 *
	 * @param keyImage The keyimage to use
	 * @return Returns a map of boolean and publicSpendKey if found, if not an empty map
	 */
	public Map<Boolean, String> getKeyImageOwner(String keyImage) {
		var ownerMap = new HashMap<Boolean, String>();

		if (isViewWallet) {
			return ownerMap;
		}

		var owner = keyImageOwners.values()
			.stream()
			.filter(ki -> ki.equals(keyImage))
			.findFirst()
			.orElse(null);

		if (owner != null) {
			ownerMap.put(true, owner);
			return ownerMap;
		}

		return ownerMap;
	}

	/**
	 * Generate the key image for an input.
	 *
	 * @param publicSpendKey The public spend key to use
	 * @param derivation     The derivation value
	 * @param outputIndex    The output index value
	 */
	public Observable<Map<String, String>> getTxInputKeyImage(String publicSpendKey, String derivation, long outputIndex) throws WalletSubNotFoundException {
		var subWallet = getSubWalletByPublicSpendKey(publicSpendKey);

		if (subWallet == null) {
			throw new WalletSubNotFoundException();
		}

		if (isViewWallet) {
			var nullKey = "";
			var map = new HashMap<String, String>();
			map.put(nullKey, nullKey);
			return Observable.just(map);
		}

		return subWallet.getTxInputKeyImage(derivation, outputIndex);
	}

	/**
	 * Returns the summed balance of the given sub wallet addresses. If none are given,
	 * take from all.
	 *
	 * @param currentHeight        The current height to use
	 * @param subWalletsToTakeFrom Which sub wallets to take from
	 * @return Observable
	 */
	public Observable<Map<Double, Double>> getBalance(long currentHeight, List<String> subWalletsToTakeFrom) {
		List<String> publicSpendKeys = new ArrayList<>();

		if (subWalletsToTakeFrom.size() == 0) {
			publicSpendKeys = this.publicSpendKeys;
		} else {
			for (var address : subWalletsToTakeFrom) {
				CryptoUtils.addressToKeys(address)
					.subscribe(str -> this.publicSpendKeys.add(str.keySet().toString()));
			}
		}

		var unlockedBalance = 0.0;
		var lockedBalance = 0.0;

		/* For faster lookups in case we have a ton of transactions or
           sub wallets to take from */
		// var lookupMap = new HashMap(publicSpendKeys)

		/*for (var transaction : transactions) {
			var unlocked = CryptoUtils.isInputUnlocked(transaction.getUnlockTime(), currentHeight);
			//TODO: finish this implementation

		}*/

		return Observable.empty();
	}

	/**
	 * Gets all addresses contained in this SubWallets container.
	 */
	public List<String> getAddresses() {
		var addresses = new ArrayList<String>();

		for (var subWallet : subWallets.values()) {
			addresses.add(subWallet.getAddress());
		}

		return addresses;
	}

	/**
	 * Get input sufficient to spend the amount passed in, from the given
	 * sub wallets, along with the keys for that inputs owner.
	 * <p>
	 * Throws if the sub wallets don't exist, or not enough money is found.
	 *
	 * @param subWalletsToTakeFrom The sub wallets to use
	 * @param currentHeight        The current height to use
	 * @return Returns the inputs and their owners, and the sum of their money
	 */
	public Observable<List<TxInputAndOwner>> getSpendableTransactionInputs(List<String> subWalletsToTakeFrom, long currentHeight) {
		var availableInputs = new ArrayList<TxInputAndOwner>();

		// loop through each sub wallet that we can take from
		for (var address : subWalletsToTakeFrom) {
			CryptoUtils.addressToKeys(address)
				.subscribe(publicSpendKeys -> {
					var key = publicSpendKeys.values()
						.stream()
						.findFirst()
						.orElse(null);

					var subWallet = subWallets.values()
						.stream()
						.filter(sw -> sw.getSpendKeys().getPublicKey().equals(key))
						.findFirst()
						.orElse(null);

					if (subWallet == null) {
						throw new WalletSubNotFoundException();
					}

					// fetch the spendable inputs
					availableInputs.addAll(subWallet.getSpendableInputs(currentHeight));
				});
		}

		// sorting by amount
		availableInputs.sort(Comparator.comparing(ai -> ai.getInput().getAmount()));

		/* push into base 10 buckets. Smallest amount buckets will come first, and
		 * largest amounts within those buckets come first */
		var buckets = new HashMap<Double, List<TxInputAndOwner>>();

		for (var input : availableInputs) {
			/* find out how many digits the amount has, i.e. 1337 has 4 digits,
               420 has 3 digits */
			var numberOfDigits = Math.floor(Math.log10(input.getInput().getAmount()) + 1);

			// grab existing array or make a new one
			var bucketTmpArr = buckets.values();
			var tmpArr = new ArrayList(bucketTmpArr);

			// add input to array
			tmpArr.add(input);

			// Update bucket with new array
			buckets.put(numberOfDigits, tmpArr);
		}

		// sorting the buckets we want first in the resulting map, first. */
		// Collections.sort(buckets.values());
		//TODO: fix this later

		var orderedList = new ArrayList<TxInputAndOwner>();

		//TODO: implement while loop
		/*while (buckets.size() > 0) {

		}*/

		return Observable.just(orderedList);
	}

	public Observable<Map<List<TxInputAndOwner>, Long>> getFusionTransactionInputs(List<String> subWalletsToTakeFrom, long mixin, long currentHeight) {
		return Observable.empty();
	}

	/**
	 * Store the private key for a given transaction.
	 *
	 * @param txPrivateKey The private transaction key to store
	 * @param txHash       The transaction hash to store
	 */
	public void storeTxPrivateKey(String txPrivateKey, String txHash) {
		transactionPrivateKeys.put(txHash, txPrivateKey);
	}

	/**
	 * Store an unconfirmed incoming amount, so we can correctly display locked
	 * balances.
	 *
	 * @param unconfirmedInput The unconfirmed input object to store
	 * @param publicSpendKey   The public spend key to use to store
	 */
	public void storeUnconfirmedIncomingInput(UnconfirmedInput unconfirmedInput, String publicSpendKey) throws WalletSubNotFoundException {
		var subWallet = getSubWalletByPublicSpendKey(publicSpendKey);

		if (subWallet == null) {
			throw new WalletSubNotFoundException();
		}

		subWallet.storeUnconfirmedIncomingInput(unconfirmedInput);
	}

	/**
	 * Get the transactions of the given sub wallet address. If no sub wallet address is given,
	 * gets all transactions.
	 *
	 * @param address        The sub wallet address to get the transaction
	 * @param includeFusions If we include fusions
	 */
	public Observable<List<Transaction>> getTransactions(String address, boolean includeFusions) {
		return filterTransactions(transactions, address, includeFusions);
	}

	/**
	 * Get the number of transactions for the given subWallet, if no subWallet is given,
	 * gets the total number of transactions in the wallet container. Can be used
	 * if you want to avoid fetching every transactions repeatedly when nothing
	 * has changed.
	 *
	 * @param address        The sub wallet address to use
	 * @param includeFusions If we include fusions
	 */
	public Observable<Long> getNumTransactions(String address, boolean includeFusions) {
		/*var numberOfTransactions = getTransactions(address, includeFusions)
				.subscribe(List::size);
		return Observable.(numberOfTransactions);*/
		return Observable.empty();
	}

	/**
	 * Get the unconfirmed transactions of the given subwallet address. If no subwallet address
	 * is given, gets all unconfirmed transactions.
	 *
	 * @param address        The sub wallet address to use
	 * @param includeFusions If we include fusions
	 */
	public Observable<List<Transaction>> getUnconfirmedTransactions(String address, boolean includeFusions) {
		return filterTransactions(lockedTransactions, address, includeFusions);
	}

	/**
	 * Get the number of unconfirmed transactions for the given subWallet, if no subWallet is given,
	 * gets the total number of unconfirmed transactions in the wallet container. Can be used
	 * if you want to avoid fetching every transactions repeatedly when nothing
	 * has changed.
	 *
	 * @param address        The sub wallet address to use
	 * @param includeFusions If we include fusions
	 */
	public Observable<Long> getNumUnconfirmedTransactions(String address, boolean includeFusions) {
//		return Observable.just(getUnconfirmedTransactions(address, includeFusions));
		return Observable.empty();
	}

	public Observable<Void> addSubWallet(long scanHeight) throws WalletIllegalViewWalletOperationException {
		if (isViewWallet) {
			// it makes no sense to add a random sub wallet to a view wallet so we throw exception
			throw new WalletIllegalViewWalletOperationException();
		}

		Address.fromEntropy("", "")
			.subscribe(address -> {
				// checking if the aderess spendkeys already exists in the sub wallet
				//TODO: Fix implementation in this

				// if they exist throw exception
					/*if (spendKeys != null) {
						throw new WalletSubWalletAlreadyExistsException();
					}*/

				// publicSpendKeys.add(address.getSpendKeys());

				Address.fromKeys(address.getSpendKeys())
					.subscribe(newAddr -> {
					});
			});

		return Observable.empty();
	}

	public Observable<Void> importSubWallet(String privateSpendKey, long scanHeight) throws WalletIllegalViewWalletOperationException {
		if (isViewWallet) {
			// it makes no sense to add a random sub wallet to a view wallet so we throw exception
			throw new WalletIllegalViewWalletOperationException();
		}

		// TODO: need to implement cryptonote from c/cpp library first to implement this method
		// var publicSpendKey = CryptoUtils.pri

		return Observable.empty();
	}

	public Observable<Void> importViewSubWallet(String publicSpendKey, long scanHeight) {
		return Observable.empty();
	}

	public Observable<Boolean> deleteSubWallet(String address) {
		/*CryptoUtils.addressToKeys(address)
				.subscribe(publicSpendKeys -> {
					var subWallet = null;

					if (subWallet == null) {
						throw new WalletAddressNotInWalletException();
					}

					if (subWallet.isPrimaryAddress()) {
						throw new WalletCannotDeletePrimaryAddressException();
					}

					subWallets.remove(publicSpendKey);

					deleteAddressTransactions(transactions, publicSpendKey);
					deleteAddressTransactions(lockedTransactions, publicSpendKey);
				});*/
		return Observable.empty();
	}

	public long getWalletCount() {
		return subWallets.size();
	}

	private void deleteAddressTransactions(List<Transaction> transactions, String publicSpendKey) {
		// transactions.removeIf(t -> t.getHash().equals(transaction.getHash()));
	}

	private Observable<List<Transaction>> filterTransactions(List<Transaction> transactions, String address, boolean includeFusions) {
		return Observable.empty();
	}

	private SubWallet getSubWalletByPublicSpendKey(String publicSpendKey) {
		return subWallets.values()
			.stream()
			.filter(sw -> sw.getSpendKeys().getPublicKey().equals(publicSpendKey))
			.findFirst()
			.orElse(null);
	}
}