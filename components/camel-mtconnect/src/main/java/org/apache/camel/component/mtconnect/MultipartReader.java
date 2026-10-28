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
package org.apache.camel.component.mtconnect;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Reads the parts of an agent's streaming response, {@code multipart/mixed} (2.x agents) or
 * {@code multipart/x-mixed-replace} (1.x agents). Part header names are compared without case, because 1.x agents write
 * {@code Content-length}. A part with a Content-Length header is complete after that many bytes, so it is delivered
 * before the next boundary arrives; a part without one ends at the next boundary. Agents end a stream without a closing
 * boundary, so the end of the input between parts is the end of the stream. The reader reads byte by byte, so its input
 * must be buffered.
 */
final class MultipartReader {

    private static final int MAX_LINE = 8192;

    private final InputStream in;
    private final byte[] delimiter;
    private final String boundaryLine;
    private boolean atBoundary;
    private boolean finished;

    MultipartReader(InputStream in, String boundary) {
        this.in = in;
        this.boundaryLine = "--" + boundary;
        this.delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * The boundary parameter of a multipart content type, or null when the content type is not multipart.
     */
    static String boundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        String[] parameters = contentType.split(";");
        if (!parameters[0].trim().toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return null;
        }
        for (int i = 1; i < parameters.length; i++) {
            String parameter = parameters[i].trim();
            int equals = parameter.indexOf('=');
            if (equals > 0 && parameter.substring(0, equals).trim().equalsIgnoreCase("boundary")) {
                String value = parameter.substring(equals + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * The content of the next part, or null when the stream ended between parts.
     *
     * @throws IOException when the stream ends inside a part or the framing is invalid
     */
    byte[] nextPart() throws IOException {
        if (finished) {
            return null;
        }
        if (!atBoundary && !skipToBoundary()) {
            finished = true;
            return null;
        }
        atBoundary = false;
        long contentLength = -1;
        while (true) {
            String line = readLine();
            if (line == null) {
                throw new EOFException("The stream ended inside the headers of a part");
            }
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    contentLength = Long.parseLong(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid part header: " + line, e);
                }
            }
        }
        return contentLength >= 0 ? readExactly(contentLength) : readToDelimiter();
    }

    /** Skips lines up to the next boundary line; false at the end of the stream or after the closing boundary. */
    private boolean skipToBoundary() throws IOException {
        while (true) {
            String line = readLine();
            if (line == null) {
                return false;
            }
            String trimmed = line.stripTrailing();
            if (trimmed.equals(boundaryLine + "--")) {
                return false;
            }
            if (trimmed.equals(boundaryLine)) {
                return true;
            }
        }
    }

    private byte[] readExactly(long length) throws IOException {
        if (length > Integer.MAX_VALUE - 8) {
            throw new IOException("Part too large: " + length + " bytes");
        }
        byte[] content = in.readNBytes((int) length);
        if (content.length < length) {
            throw new EOFException("The stream ended inside a part");
        }
        return content;
    }

    /** Reads up to the delimiter CRLF--boundary and consumes the rest of the boundary line. */
    private byte[] readToDelimiter() throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        byte[] window = new byte[delimiter.length];
        int filled = 0;
        while (true) {
            int next = in.read();
            if (next < 0) {
                throw new EOFException("The stream ended inside a part");
            }
            if (filled == window.length) {
                content.write(window[0]);
                System.arraycopy(window, 1, window, 0, window.length - 1);
                filled--;
            }
            window[filled++] = (byte) next;
            if (filled == window.length && Arrays.equals(window, delimiter)) {
                String rest = readLine();
                if (rest != null && rest.stripTrailing().equals("--")) {
                    finished = true;
                }
                atBoundary = true;
                return content.toByteArray();
            }
        }
    }

    /** One line without its CR LF or LF, or null at the end of the stream before any byte. */
    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int next = in.read();
            if (next < 0) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
            }
            if (next == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            if (line.size() >= MAX_LINE) {
                throw new IOException("Line longer than " + MAX_LINE + " bytes in the multipart stream");
            }
            line.write(next);
        }
    }
}
