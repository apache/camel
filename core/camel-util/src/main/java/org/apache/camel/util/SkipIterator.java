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

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Skip based {@link Iterator} which skips the given {@link Iterator} a number of times.
 */
public final class SkipIterator implements Iterator<Object>, Closeable {

    private final Iterator<?> it;
    private final int skip;
    private boolean closed;
    private final AtomicBoolean hasSkip = new AtomicBoolean();

    /**
     * Creates a new skip iterator
     *
     * @param it        the iterator
     * @param skip      number of times to skip
     * @throws IllegalArgumentException is thrown if skip is not a positive number
     */
    public SkipIterator(Iterator<?> it, int skip) {
        this.it = it;
        this.skip = skip;
        if (skip < 0) {
            throw new IllegalArgumentException("Skip must not be a negative number, was: " + skip);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            IOHelper.closeIterator(it);
        } finally {
            // we are now closed
            closed = true;
        }
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }

        if (hasSkip.compareAndSet(false, true)) {
            doSkip();
        }

        boolean answer = it.hasNext();
        if (!answer) {
            // auto close
            try {
                close();
            } catch (IOException e) {
                // ignore
            }
        }
        return answer;
    }

    @Override
    public Object next() {
        if (hasSkip.compareAndSet(false, true)) {
            doSkip();
        }

        return it.next();
    }

    private void doSkip() {
        for (int i = 0; i < skip; i++) {
            if (it.hasNext()) {
                // skip
                it.next();
            }
        }
    }

    @Override
    public void remove() {
        it.remove();
    }
}
