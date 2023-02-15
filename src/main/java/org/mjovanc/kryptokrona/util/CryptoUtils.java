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

package org.mjovanc.kryptokrona.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.reactivex.rxjava3.core.Observable;
import org.mjovanc.kryptokrona.config.Constants;
import org.mjovanc.kryptokrona.exception.wallet.WalletMnemonicInvalidWordException;
import org.mjovanc.kryptokrona.exception.wallet.WalletMnemonicWrongLengthException;
import org.mjovanc.kryptokrona.model.util.WordList;
import org.mjovanc.kryptokrona.wallet.Address;
import org.mjovanc.kryptokrona.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.*;

/**
 * CryptoUtils.java
 *
 * @author Marcus Cvjeticanin (@mjovanc)
 */
public class CryptoUtils {

	private static Gson gson = new Gson();

	private List<String> words;

	private static final String jsonFileName = "wordlist.json";

	private static final Type collectionType = new TypeToken<List<String>>() {
	}.getType();

	private static final Logger logger = LoggerFactory.getLogger(CryptoUtils.class);

	public static Observable<Map<String, String>> addressToKeys(String address) {
		return Observable.empty();
	}

	public static long getLowerBound(long val, long nearestMultiple) {
		var remainder = val % nearestMultiple;
		return val - remainder;
	}

	public static long getUpperBound(long val, long nearestMultiple) {
		return getLowerBound(val, nearestMultiple) + nearestMultiple;
	}

	/**
	 * Get a decent value to start the sync process at
	 *
	 * @return timestamp
	 */
	public static long getCurrentTimestampAdjusted() {
		return getCurrentTimestampAdjusted(Config.BLOCK_TARGET_TIME);
	}

	/**
	 * Get a decent value to start the sync process at
	 *
	 * @param blockTargetTime
	 * @return timestamp
	 */
	public static long getCurrentTimestampAdjusted(long blockTargetTime) {
		var timestamp = Math.floor((double) Instant.now().toEpochMilli() / 1000);
		var currentTimestampAdjusted = timestamp - (100 * blockTargetTime);

		return (long) currentTimestampAdjusted;
	}

	public static boolean isInputUnlocked(long unlockTime, long currenHeight) {
		/* Might as well return fast with the case that is true for nearly all
           transactions (excluding coinbase) */
		if (unlockTime == 0) {
			return true;
		}

		if (unlockTime >= Constants.MAX_BLOCK_NUMBER) {
			return (Math.floor((double) Instant.now().toEpochMilli() / 1000)) >= unlockTime;
		} else {
			return currenHeight + 1 >= unlockTime;
		}
	}

	/**
	 * Takes an amount in atomic units and pretty prints it.
	 * Example: 12345607 -> XKR 123,456.07
	 *
	 * @param amount The amount to pretty print
	 * @return Returns a pretty print representation of the amount
	 */
	public static String prettyPrintAmount(double amount) {
		var locale = new Locale("en", "US");
		DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getCurrencyInstance(locale);
		DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(locale);
		dfs.setCurrencySymbol("XKR ");
		decimalFormat.setDecimalFormatSymbols(dfs);

		return String.format("%s", decimalFormat.format(amount));
	}

	public Observable<Void> delay(long ms) {
		return Observable.empty();
	}

	/**
	 * Split each amount into uniform amounts, e.g.
	 * 1234567 = 1000000 + 200000 + 30000 + 4000 + 500 + 60 + 7
	 *
	 * @param amount                 The amount to split
	 * @param preventTooLargeOutputs If we should prevent too large outputs
	 * @return Returns a list of uniform amounts
	 */
	public static List<Double> splitAmountIntoDenominations(double amount, boolean preventTooLargeOutputs) {
		var multiplier = 1;

		var splitAmounts = new ArrayList<Double>();

		while (amount >= 1) {
			var denomination = multiplier * (amount % 10);

			if (denomination > Constants.MAX_OUTPUT_SIZE_CLIENT && preventTooLargeOutputs) {
				// split amounts into ten chunks
				var numSplitAmounts = 10.0;
				var splitAmount = denomination / 10;

				while (splitAmount > Constants.MAX_OUTPUT_SIZE_CLIENT) {
					splitAmount = Math.floor(splitAmount / 10);
					numSplitAmounts *= 10.0;
				}

				var arr = new ArrayList<>(List.of(numSplitAmounts));
				Collections.fill(arr, splitAmount);

				splitAmounts.addAll(arr);
			} else if (denomination != 0) {
				splitAmounts.add(denomination);
			}

			amount = Math.floor(amount / 10);
			multiplier *= 10;
		}

		return splitAmounts;
	}

	/**
	 * The formula for the block size is as follows. Calculate the
	 * maxBlockCumulativeSize. This is equal to:
	 * 100,000 + ((height * 102,400) / 1,051,200)
	 * At a block height of 400k, this gives us a size of 138,964.
	 * The constants this calculation arise from can be seen below, or in
	 * src/CryptoNoteCore/Currency.cpp::maxBlockCumulativeSize(). Call this value
	 * x.
	 * <p>
	 * Next, calculate the median size of the last 100 blocks. Take the max of
	 * this value, and 100,000. Multiply this value by 1.25. Call this value y.
	 * <p>
	 * Finally, return the minimum of x and y.
	 * <p>
	 * Or, in short: min(140k (slowly rising), 1.25 * max(100k, median(last 100 blocks size)))
	 * Block size will always be 125k or greater (Assuming non testnet)
	 * <p>
	 * To get the max transaction size, remove 600 from this value, for the
	 * reserved miner transaction.
	 * <p>
	 * We are going to ignore the median(last 100 blocks size), as it is possible
	 * for a transaction to be valid for inclusion in a block when it is submitted,
	 * but not when it actually comes to be mined, for example if the median
	 * block size suddenly decreases. This gives a bit of a lower cap of max
	 * tx sizes, but prevents anything getting stuck in the pool.
	 *
	 * @param currentHeight The current height to use
	 * @param blockTime     The blocktime to use
	 * @return Gets the max transaction size
	 */
	public static long getMaxTxSize(long currentHeight, long blockTime) {
		long numerator = currentHeight * Constants.MAX_BLOCK_SIZE_GROWTH_SPEED_NUMERATOR;
		long denominator = (Constants.MAX_BLOCK_SIZE_GROWTH_SPEED_DENOMINATOR / blockTime);
		long growth = numerator / denominator;
		long x = Constants.MAX_BLOCK_SIZE_INITIAL + growth;
		long y = 125000;

		/* Need space for the miner transaction */
		return Math.min(x, y) - Constants.CRYPTONOTE_COINBASE_BLOB_RESERVED_SIZE;
	}

	/**
	 * Converts an amount in bytes, say, 10000, into 9.76 KB
	 *
	 * @param bytes The amount of bytes to convert
	 * @return Gets the amount in bytes to human readable format
	 */
	public static String prettyPrintBytes(double bytes) {
		var suffixes = new ArrayList<String>(List.of("B", "KB", "MB", "GB", "TB"));

		var selectedSuffixes = 0;

		while (bytes >= 1024 && selectedSuffixes < suffixes.size() - 1) {
			selectedSuffixes++;
			bytes /= 1024;
		}

		return String.format("%.2f %s", bytes, suffixes.get(selectedSuffixes));
	}

	/**
	 * Returns whether the given word is in the mnemonic english dictionary. Note that
	 * just because all the words are valid, does not mean the mnemonic is valid.
	 * <p>
	 * Use isValidMnemonic to verify that.
	 *
	 * @param word The word to test
	 */
	public static boolean isValidMnemonicWord(String word) {
		return WordList.WORD_LIST.contains(word);
	}

	/**
	 * Verifies whether a mnemonic is valid. Returns a boolean, and an error messsage
	 * describing what is invalid.
	 *
	 * @param mnemonicWords The mnemonic to verify
	 */
	public static Observable<Boolean> isValidMnemonic(List<String> mnemonicWords) throws WalletMnemonicWrongLengthException, WalletMnemonicInvalidWordException {
		if (mnemonicWords.size() != 25) {
			throw new WalletMnemonicWrongLengthException();
		}

		var invalidWords = new ArrayList<String>();

		for (var word : mnemonicWords) {
			if (!isValidMnemonicWord(word)) {
				invalidWords.add(word);
			}
		}

		if (invalidWords.size() != 0) {
			throw new WalletMnemonicInvalidWordException();
		}

		var address = Address.fromMnemonic(mnemonicWords, null);

		//TODO: return true if it exists, false if not
		return Observable.empty();
	}

	public static double getMinimumTransactionFee(long transactionSize, long height) {
		return getTransactionFee(transactionSize, Config.MINIMUM_FEE_PER_BYTE);
	}

	public static double getTransactionFee(long transactionSize, double feePerByte) {
		var numChunks = Math.ceil(transactionSize / Config.FEE_PER_BYTE_CHUNK_SIZE);

		return numChunks * feePerByte * Config.FEE_PER_BYTE_CHUNK_SIZE;
	}

	public static long estimatedTransactionSize(long mixin, long numInputs, long numOutputs, boolean havePaymentID, long extraDataSize) {
		var KEY_IMAGE_SIZE = 32;
		var OUTPUT_KEY_SIZE = 32;
		var AMOUNT_SIZE = 8 + 2;
		var GLOBAL_INDEXES_VECTOR_SIZE_SIZE = 1;
		var GLOBAL_INDEXES_INITIAL_VALUE_SIZE = 4;
		var SIGNATURE_SIZE = 64;
		var EXTRA_TAG_SIZE = 1;
		var INPUT_TAG_SIZE = 1;
		var OUTPUT_TAG_SIZE = 1;
		var PUBLIC_KEY_SIZE = 32;
		var TRANSACTION_VERSION_SIZE = 1;
		var TRANSACTION_UNLOCK_TIME_SIZE = 8 + 2;
		var EXTRA_DATA_SIZE = extraDataSize > 0 ? extraDataSize + 4 : 0;
		var PAYMENT_ID_SIZE = havePaymentID ? 34 : 0;

		// the size of the transaction header
		var headerSize = TRANSACTION_VERSION_SIZE + TRANSACTION_UNLOCK_TIME_SIZE + EXTRA_TAG_SIZE + EXTRA_DATA_SIZE + PUBLIC_KEY_SIZE + PAYMENT_ID_SIZE;

		// the size of each transaction input
		var inputSize = INPUT_TAG_SIZE + AMOUNT_SIZE + KEY_IMAGE_SIZE + SIGNATURE_SIZE + GLOBAL_INDEXES_VECTOR_SIZE_SIZE + GLOBAL_INDEXES_INITIAL_VALUE_SIZE + mixin * SIGNATURE_SIZE;

		var inputsSize = inputSize * numInputs;

		var outputSize = OUTPUT_TAG_SIZE + OUTPUT_KEY_SIZE + AMOUNT_SIZE;

		var outputsSize = outputSize * numOutputs;

		return headerSize + inputsSize + outputsSize;
	}
}