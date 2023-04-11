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
package org.apache.camel.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link InputStream} that skips the last byte of the underlying delegate {@link InputStream} if the last byte is
 * equal to the given {@code matchLast} value.
 */
public class SkipLastByteInputStream extends BufferedInputStream {

    private final byte matchLast;

    public SkipLastByteInputStream(InputStream delegate, byte matchLast) {
        super(delegate);
        this.matchLast = matchLast;
    }

    public SkipLastByteInputStream(InputStream delegate, int size, byte matchLast) {
        super(delegate, size);
        this.matchLast = matchLast;
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c < 0) {
            return -1;
        } else if (c == matchLast) {
            /* look ahead */
            super.mark(1);
            int nextC = super.read();
            if (nextC < 0) {
                /* matchLast is the last byte */
                return -1;
            }
            super.reset();
        }
        return c;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
        final int count = super.read(buffer, off, len);
        if (count < 0) {
            return -1;
        }
        final int lastIndex = off + count - 1;
        if (lastIndex >= 0) {
            byte lastByte = buffer[lastIndex];
            if (lastByte == matchLast) {
                /* look ahead */
                super.mark(1);
                int nextC = super.read();
                if (nextC < 0) {
                    /* matchLast is the last byte - cut it away and do not reset */
                    return count - 1;
                } else {
                    super.reset();
                }
            }
        }
        return count;
    }

    @Override
    public boolean markSupported() {
        /* we do not want callers to mess with mark() and reset() because we use it ourselves */
        return false;
    }

    @Override
    public synchronized long skip(long n) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void reset() {
        throw new UnsupportedOperationException();
    }

}
