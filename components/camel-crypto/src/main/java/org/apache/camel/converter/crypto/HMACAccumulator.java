/*
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
package org.apache.camel.converter.crypto;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Key;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.apache.camel.converter.crypto.HexUtils.byteArrayToHexString;

/**
 * <code>HMACAccumulator</code> is used to build Hash Message Authentication
 * Codes. It has two modes, one where all the data acquired is used to build the
 * MAC and a second that assumes that the last n bytes of the acquired data will
 * contain a MAC for all the data previous.
 * <p>
 * The first mode it used in an encryption phase to create a MAC for the
 * encrypted data. The second mode is used in the decryption phase and
 * recalculates the MAC taking into account that for cases where the encrypted
 * data MAC has been appended. Internally the accumulator uses a circular buffer
 * to simplify the housekeeping of avoiding the last n bytes.
 * <p>
 * It is assumed that the supplied buffersize is always greater than or equal to
 * the mac length.
 */
public class HMACAccumulator {

    protected OutputStream outputStream;
    private CircularBuffer unprocessed;
    private byte[] calculatedMac;
    private Mac hmac;
    private int maclength;
    private byte[] appended;

    HMACAccumulator() {
    }

    public HMACAccumulator(Key key, String macAlgorithm, String cryptoProvider, int buffersize) throws Exception {
        hmac = cryptoProvider == null ? Mac.getInstance(macAlgorithm) : Mac.getInstance(macAlgorithm, cryptoProvider);
        Key hmacKey = new SecretKeySpec(key.getEncoded(), macAlgorithm);
        hmac.init(hmacKey);
        maclength = hmac.getMacLength();
        unprocessed = new CircularBuffer(buffersize + maclength);
    }

    /**
     * Update buffer with MAC. Typically used in the encryption phase where no
     * hmac is appended to the buffer.
     */
    public void encryptUpdate(byte[] buffer, int read) {
        hmac.update(buffer, 0, read);
    }

    /**
     * Update buffer with MAC taking into account that a MAC is appended to the
     * buffer and should be precluded from the MAC calculation.
     */
    public void decryptUpdate(byte[] buffer, int read) throws IOException {
        unprocessed.write(buffer, 0, read);
        int safe = unprocessed.availableForRead() - maclength;
        if (safe > 0) {
            unprocessed.read(buffer, 0, safe);
            hmac.update(buffer, 0, safe);
            if (outputStream != null) {
                outputStream.write(buffer, 0, safe);
            }
        }
    }

    public byte[] getCalculatedMac() {
        if (calculatedMac == null) {
            calculatedMac = hmac.doFinal();
        }
        return calculatedMac;
    }

    public byte[] getAppendedMac() {
        if (appended == null) {
            appended = new byte[maclength];
            unprocessed.read(appended, 0, maclength);
        }
        return appended;
    }

    public void validate() {
        byte[] actual = getCalculatedMac();
        byte[] expected = getAppendedMac();
        for (int x = 0; x < actual.length; x++) {
            if (expected[x] != actual[x]) {
                throw new IllegalStateException("Expected mac did not match actual mac\nexpected:"
                    + byteArrayToHexString(expected) + "\n     actual:" + byteArrayToHexString(actual));
            }
        }
    }

    public int getMaclength() {
        return maclength;
    }

    public void attachStream(final OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    static class CircularBuffer {
        private byte[] buffer;
        private int write;
        private int read;
        private int available;

        CircularBuffer(int bufferSize) {
            buffer = new byte[bufferSize];
            available = bufferSize;
        }

        public void write(byte[] data, int pos, int len) {
            if (available >= len) {
                if (write + len > buffer.length) {
                    int overlap = write + len % buffer.length;
                    System.arraycopy(data, 0, buffer, write, len - overlap);
                    System.arraycopy(data, len - overlap, buffer, 0, overlap);
                } else {
                    System.arraycopy(data, pos, buffer, write, len);
                }
                write = (write + len) % buffer.length;
                available -= len;
            }
        }

        public int read(byte[] dest, int position, int len) {
            if (dest.length - position >= len) {
                if (buffer.length - available >= len) {
                    int overlap = (read + len) % buffer.length;
                    if (read > write) {
                        int x = buffer.length - read;
                        System.arraycopy(buffer, read, dest, position, buffer.length - read);
                        System.arraycopy(buffer, 0, dest, position + x, overlap);
                    } else {
                        System.arraycopy(buffer, read, dest, position, len);
                    }
                    read = (read + len) % buffer.length;
                    available += len;
                    return len;
                }
            }
            return 0;
        }

        public boolean compareTo(byte[] compare, int pos, int len) {
            boolean equal = false;
            if (len <= availableForRead()) {
                int x = 0;
                while (equal && x < len) {
                    equal = compare[pos + x] != buffer[read + x % buffer.length];
                }
            }
            return equal;
        }

        public int availableForRead() {
            return buffer.length - available;
        }

        public int availableForWrite() {
            return available;
        }

        public String show() {
            StringBuilder b = new StringBuilder(HexUtils.byteArrayToHexString(buffer)).append("\n");
            for (int x = read; --x >= 0;) {
                b.append("--");
            }
            b.append("r");
            b.append("\n");
            for (int x = write; --x >= 0;) {
                b.append("--");
            }
            b.append("w");
            b.append("\n");
            return b.toString();
        }
    }

}
