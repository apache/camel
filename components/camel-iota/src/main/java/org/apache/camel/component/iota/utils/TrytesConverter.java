/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.iota.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public final class TrytesConverter {
    public static final String TRYTE_ALPHABET = "9ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int NUMBER_OF_TRITS_IN_A_TRYTE = 3;
    private static final int NUMBER_OF_TRITS_IN_A_BYTE = 5;

    private static final int RADIX = 3;
    private static final int MAX_TRIT_VALUE = (RADIX - 1) / 2;
    private static final int MIN_TRIT_VALUE = -MAX_TRIT_VALUE;

    private static final int[][] BYTE_TO_TRITS_MAPPINGS = new int[243][];
    private static final int[][] TRYTE_TO_TRITS_MAPPINGS = new int[27][];

    static {

        final int[] trits = new int[NUMBER_OF_TRITS_IN_A_BYTE];

        for (int i = 0; i < 243; i++) {
            BYTE_TO_TRITS_MAPPINGS[i] = Arrays.copyOf(trits, NUMBER_OF_TRITS_IN_A_BYTE);
            increment(trits, NUMBER_OF_TRITS_IN_A_BYTE);
        }

        for (int i = 0; i < 27; i++) {
            TRYTE_TO_TRITS_MAPPINGS[i] = Arrays.copyOf(trits, NUMBER_OF_TRITS_IN_A_TRYTE);
            increment(trits, NUMBER_OF_TRITS_IN_A_TRYTE);
        }
    }

    private TrytesConverter() {
    }
    
    /**
     * toTrytes
     * 
     * @param inputString The input String.
     * @return The ASCII char "Z" is represented as "IC" in trytes.
     */
    public static String toTrytes(String inputString) {
        StringBuilder trytes = new StringBuilder();

        for (int i = 0; i < inputString.length(); i++) {
            char asciiValue = inputString.charAt(i);

            // If not recognizable ASCII character, replace with space
            if (asciiValue > 255) {
                asciiValue = 32;
            }

            int firstValue = asciiValue % 27;
            int secondValue = (asciiValue - firstValue) / 27;

            String trytesValue = String.valueOf(TRYTE_ALPHABET.charAt(firstValue) + String.valueOf(TRYTE_ALPHABET.charAt(secondValue)));

            trytes.append(trytesValue);
        }

        return trytes.toString();
    }

    /**
     * Converts trytes into trits.
     *
     * @param trytes The trytes to be converted.
     * @return Array of trits.
     **/
    public static int[] trits(final String trytes) {
        int[] d = new int[3 * trytes.length()];
        for (int i = 0; i < trytes.length(); i++) {
            System.arraycopy(TRYTE_TO_TRITS_MAPPINGS[TRYTE_ALPHABET.indexOf(trytes.charAt(i))], 0, d, i * NUMBER_OF_TRITS_IN_A_TRYTE, NUMBER_OF_TRITS_IN_A_TRYTE);
        }
        return d;
    }

    /**
     * Increments the specified trits.
     *
     * @param trits The trits.
     * @param size The size.
     */
    public static void increment(final int[] trits, final int size) {

        for (int i = 0; i < size; i++) {
            if (++trits[i] > MAX_TRIT_VALUE) {
                trits[i] = MIN_TRIT_VALUE;
            } else {
                break;
            }
        }
    }

    /**
     * Converts the specified trits to its corresponding integer value.
     *
     * @param trits The trits.
     * @return The value.
     */
    public static long longValue(final int[] trits) {
        long value = 0;

        for (int i = trits.length; i-- > 0;) {
            value = value * 3 + trits[i];
        }
        return value;
    }

    public static Map<String, Object> transactionObject(final String trytes) {
        Map<String, Object> result = new HashMap<>();

        if (StringUtils.isEmpty(trytes)) {
            throw new UnsupportedOperationException("Trytes is empty");
        }

        // validity check
        for (int i = 2279; i < 2295; i++) {
            if (trytes.charAt(i) != '9') {
                throw new UnsupportedOperationException("Trytes does not seem a valid tryte");
            }
        }

        int[] transactionTrits = trits(trytes);

        result.put("signatureFragments", trytes.substring(0, 2187));
        result.put("address", trytes.substring(2187, 2268));
        result.put("value", longValue(Arrays.copyOfRange(transactionTrits, 6804, 6837)));
        result.put("obsoleteTag", trytes.substring(2295, 2322));
        result.put("timestamp", longValue(Arrays.copyOfRange(transactionTrits, 6966, 6993)));
        result.put("currentIndex", longValue(Arrays.copyOfRange(transactionTrits, 6993, 7020)));
        result.put("lastIndex", longValue(Arrays.copyOfRange(transactionTrits, 7020, 7047)));
        result.put("bundle", trytes.substring(2349, 2430));
        result.put("trunkTransaction", trytes.substring(2430, 2511));
        result.put("branchTransaction", trytes.substring(2511, 2592));
        result.put("tag", trytes.substring(2592, 2619));
        result.put("attachmentTimestamp", longValue(Arrays.copyOfRange(transactionTrits, 7857, 7884)));
        result.put("attachmentTimestampLowerBound", longValue(Arrays.copyOfRange(transactionTrits, 7884, 7911)));
        result.put("attachmentTimestampUpperBound", longValue(Arrays.copyOfRange(transactionTrits, 7911, 7938)));
        result.put("nonce", trytes.substring(2646, 2673));

        return result;
    }
}
