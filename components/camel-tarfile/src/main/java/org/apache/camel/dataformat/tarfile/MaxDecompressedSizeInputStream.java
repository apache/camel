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
package org.apache.camel.dataformat.tarfile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link InputStream} wrapper that enforces a maximum number of decompressed bytes read, throwing an
 * {@link IOException} when the limit is exceeded.
 */
class MaxDecompressedSizeInputStream extends FilterInputStream {

    private final long maxDecompressedSize;
    private long totalRead;

    MaxDecompressedSizeInputStream(InputStream in, long maxDecompressedSize) {
        super(in);
        this.maxDecompressedSize = maxDecompressedSize;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            totalRead++;
            checkLimit();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            totalRead += n;
            checkLimit();
        }
        return n;
    }

    private void checkLimit() throws IOException {
        if (totalRead > maxDecompressedSize) {
            throw new IOException("The InputStream entry being decompressed exceeds the maximum allowed size");
        }
    }
}
