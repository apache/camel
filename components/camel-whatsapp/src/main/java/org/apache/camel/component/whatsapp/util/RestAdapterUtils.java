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
package org.apache.camel.component.whatsapp.util;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public final class RestAdapterUtils {

    public static final byte[] EXTRA_BYTES = "--".getBytes();
    public static final byte[] NEW_LINE_BYTES = "\r\n".getBytes();
    public static final byte[] CONTENT_DISPOSITION_BYTES = "Content-Disposition: form-data; name=".getBytes();
    public static final byte[] QUOTE_BYTES = "\"".getBytes();
    public static final byte[] FILE_NAME_HEADER_BYTES = "\"; filename=\"".getBytes();
    public static final byte[] CONTENT_TYPE_HEADER_BYTES = "\"\r\nContent-Type: ".getBytes();

    private RestAdapterUtils() {
    }

    public static ByteBuffer generateByteBuffer(byte[]... bytes) {
        int size = Arrays.stream(bytes).map(b -> b.length).reduce(0, Integer::sum);

        return getByteBuffer(size, bytes);
    }

    private static ByteBuffer getByteBuffer(int size, byte[][] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(size);

        for (byte[] byteArray : bytes) {
            buffer.put(byteArray);
        }

        return buffer;
    }

    public static ByteBuffer generateByteBuffer(List<byte[]> bytes) {
        int size = bytes.stream().map(b -> b.length).reduce(0, Integer::sum);

        ByteBuffer buffer = ByteBuffer.allocate(size);

        for (byte[] byteArray : bytes) {
            buffer.put(byteArray);
        }

        return buffer;
    }
}
