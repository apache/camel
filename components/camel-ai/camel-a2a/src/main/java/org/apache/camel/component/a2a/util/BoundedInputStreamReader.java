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
package org.apache.camel.component.a2a.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads response bodies from untrusted peers with an explicit byte cap.
 */
public final class BoundedInputStreamReader {

    private static final int BUFFER_SIZE = 8192;

    private BoundedInputStreamReader() {
    }

    public static byte[] readAtMost(InputStream input, long maxBytes, String description) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be non-negative");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        while (true) {
            long remaining = maxBytes - total;
            int allowed = remaining >= buffer.length ? buffer.length : (int) remaining + 1;
            int read = input.read(buffer, 0, allowed);
            if (read == -1) {
                return output.toByteArray();
            }
            total += read;
            if (total > maxBytes) {
                throw new IOException(description + " exceeds maximum size: " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
    }

    public static byte[] readAtMostAndClose(InputStream input, long maxBytes, String description) throws IOException {
        try (InputStream body = input) {
            return readAtMost(body, maxBytes, description);
        }
    }
}
