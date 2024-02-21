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
package org.apache.camel.jsonpath;

import java.io.CharConversionException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Special stream for JSON streams. Determines from the first 4 bytes the JSON encoding according to JSON specification
 * RFC-4627 or newer. In addition BOMs are taken into account.
 * <p>
 * This class is not thread safe.
 */
public class JsonStream extends FilterInputStream {

    private static final byte[] BOM_UTF_32BE = new byte[] { 0x00, 0x00, (byte) 0xFE, (byte) 0xFF };

    private static final byte[] BOM_UTF_32LE = new byte[] { (byte) 0xFF, (byte) 0xFE, 0x00, 0x00 };

    private static final byte[] BOM_UTF_32_2143 = new byte[] { 0x00, 0x00, (byte) 0xFF, (byte) 0xFE };

    private static final byte[] BOM_UTF_32_3412 = new byte[] { (byte) 0xFE, (byte) 0xFF, 0x00, 0x00 };

    private static final byte[] BOM_UTF_16BE = new byte[] { (byte) 0xFE, (byte) 0xFF };

    private static final byte[] BOM_UTF_16LE = new byte[] { (byte) 0xFF, (byte) 0xFE };

    private static final byte[] BOM_UTF_8 = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    private final byte[] fourByteBuffer = new byte[4];

    /* input index of the four byte buffer (BOMs are skipped) */
    private int inputIndex;

    /* read bytes into the buffer */
    private int inputEnd;

    private final Charset encoding;

    /**
     * Constructor. Determines the encoding during the instantiation according to JSON specification RFC-4627 or newer.
     * In addition BOMs are taken into account.
     *
     * @param  in                       input stream must contain a JSON content
     * @throws IOException              if an error occurs during the determination of the encoding
     * @throws CharConversionException  if the UCS4 endianess 2143 or 3412 is used
     * @throws IllegalArgumentException if the input stream is <code>null</code>
     */
    public JsonStream(InputStream in) throws IOException {
        super(in);
        if (in == null) {
            throw new IllegalArgumentException("input stream is null");
        }

        inputEnd = inputIndex = 0;

        Charset enc = null;

        if (loadAtLeast(4)) {

            enc = getEncodingFromBOM();
            if (enc == null) {
                // no BOM
                enc = getUTF32EncodingFromNullPattern();
                if (enc == null) {
                    enc = getUTF16EncodingFromNullPattern();
                }
            }
        } else if (loadAtLeast(2)) {
            enc = getUTF16EncodingFromNullPattern();
        }

        if (enc == null) {
            // not found; as per specification, this means it must be UTF-8.
            enc = StandardCharsets.UTF_8;
        }
        encoding = enc;
    }

    public Charset getEncoding() {
        return encoding;
    }

    private boolean loadAtLeast(int minimum) throws IOException {

        int received = inputEnd - inputIndex;
        while (received < minimum) {
            int count = in.read(fourByteBuffer, inputEnd, fourByteBuffer.length - inputEnd);
            if (count < 1) {
                return false;
            }
            inputEnd += count;
            received += count;
        }
        return true;
    }

    private Charset getEncodingFromBOM() throws IOException {
        // 32-bit encoding BOMs
        if (Arrays.equals(fourByteBuffer, BOM_UTF_32BE)) {
            inputIndex = 4;
            return Charset.forName("UTF-32BE");
        } else if (Arrays.equals(fourByteBuffer, BOM_UTF_32LE)) {
            inputIndex = 4;
            return Charset.forName("UTF-32LE");
        } else if (Arrays.equals(fourByteBuffer, BOM_UTF_32_2143)) {
            throw getExceptionUnsupportedUCS4("2143");
        } else if (Arrays.equals(fourByteBuffer, BOM_UTF_32_3412)) {
            throw getExceptionUnsupportedUCS4("3412");
        }

        byte[] firstTwoBytes = Arrays.copyOf(fourByteBuffer, 2);
        //  16-bit encoding BOMs
        if (Arrays.equals(firstTwoBytes, BOM_UTF_16BE)) {
            inputIndex = 2;
            return StandardCharsets.UTF_16BE;
        }
        if (Arrays.equals(firstTwoBytes, BOM_UTF_16LE)) {
            inputIndex = 2;
            return StandardCharsets.UTF_16LE;
        }

        byte[] firstThreeBytes = Arrays.copyOf(fourByteBuffer, 3);
        // UTF-8 BOM?
        if (Arrays.equals(firstThreeBytes, BOM_UTF_8)) {
            inputIndex = 3;
            return StandardCharsets.UTF_8;
        }
        return null;
    }

    private Charset getUTF32EncodingFromNullPattern() throws IOException {
        //content without BOM
        if (fourByteBuffer[0] == 0 && fourByteBuffer[1] == 0 && fourByteBuffer[2] == 0) {
            //  00 00 00 xx
            return Charset.forName("UTF-32BE");
        } else if (fourByteBuffer[1] == 0 && fourByteBuffer[2] == 0 && fourByteBuffer[3] == 0) {
            // xx 00 00 00
            return Charset.forName("UTF-32LE");
        } else if (fourByteBuffer[0] == 0 && fourByteBuffer[2] == 0 && fourByteBuffer[3] == 0) {
            // 00 xx 00 00
            throw getExceptionUnsupportedUCS4("3412");
        } else if (fourByteBuffer[0] == 0 && fourByteBuffer[1] == 0 && fourByteBuffer[3] == 0) {
            //00 00 xx 00
            throw getExceptionUnsupportedUCS4("2143");
        } else {
            // Cannot be valid UTF-32 encoded JSON...
            return null;
        }
    }

    private Charset getUTF16EncodingFromNullPattern() {
        if (fourByteBuffer[0] == 0) {
            return StandardCharsets.UTF_16BE;
        } else if (fourByteBuffer[1] == 0) {
            return StandardCharsets.UTF_16LE;
        } else { // not  UTF-16
            return null;
        }
    }

    private CharConversionException getExceptionUnsupportedUCS4(String type) {
        return new CharConversionException("Unsupported UCS-4 endianness (" + type + ") detected");
    }

    @Override
    public int read() throws IOException {
        if (inputIndex < inputEnd) {
            int result = fourByteBuffer[inputIndex];
            inputIndex++;
            return result;
        }
        try {
            return in.read();
        } catch (java.io.EOFException ex) {
            return -1;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {

        if (inputIndex < inputEnd) {
            int minimum = Math.min(b.length, inputEnd - inputIndex);
            for (int i = 0; i < minimum; i++) {
                b[i] = fourByteBuffer[inputIndex];
                inputIndex++;
            }
            int rest = b.length - minimum;
            if (rest == 0) {
                return minimum;
            }
            try {
                int additionalRead = in.read(b, minimum, rest);
                if (additionalRead < 0) {
                    return minimum;
                } else {
                    return minimum + additionalRead;
                }
            } catch (java.io.EOFException ex) {
                return minimum;
            }
        } else {
            return read(b, 0, b.length);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (inputIndex < inputEnd) {
            int minimum = Math.min(b.length, inputEnd - inputIndex);
            for (int i = 0; i < minimum; i++) {
                b[off + i] = fourByteBuffer[inputIndex];
                inputIndex++;
            }
            int rest = b.length - minimum;
            if (rest == 0) {
                return minimum;
            }
            try {
                int additionalRead = in.read(b, minimum + off, rest);
                if (additionalRead < 0) {
                    return minimum;
                } else {
                    return minimum + additionalRead;
                }
            } catch (java.io.EOFException ex) {
                return minimum;
            }
        } else {

            try {
                return in.read(b, off, len);
            } catch (java.io.EOFException ex) {
                return -1;
            }
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (inputIndex < inputEnd) {
            long minimum = Math.min(n, (long) inputEnd - inputIndex);
            for (int i = 0; i < minimum; i++) {
                inputIndex++;
            }
            long rest = n - minimum;
            if (rest == 0) {
                return minimum;
            }
            long additionalSkipped = in.skip(rest);
            return additionalSkipped + minimum;
        } else {
            return in.skip(n);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("reset not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}
