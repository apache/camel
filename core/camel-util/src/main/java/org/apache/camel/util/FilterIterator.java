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
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * A filtering iterator
 */
public class FilterIterator<T> implements Iterator<T>, Closeable {

    private Iterator<T> it;
    private Predicate<T> filter;
    private T next;
    private boolean closed;

    public FilterIterator(Iterator<T> it) {
        this(it, null);
    }

    public FilterIterator(Iterator<T> it, Predicate<T> filter) {
        this.it = it;
        this.filter = filter;
    }

    @Override
    public void close() throws IOException {
        try {
            IOHelper.closeIterator(it);
        } finally {
            // we are now closed
            closed = true;
            next = null;
        }
    }

    @Override
    public boolean hasNext() {
        if (next == null) {
            next = checkNext();
        }
        return next != null;
    }

    @Override
    public T next() {
        if (next == null) {
            next = checkNext();
        }
        if (next != null) {
            T ep = next;
            next = null;
            return ep;
        }
        throw new NoSuchElementException();
    }

    @Override
    public void remove() {
        it.remove();
    }

    protected T checkNext() {
        while (!closed && it.hasNext()) {
            T ep = it.next();
            if (ep != null && (filter == null || filter.test(ep))) {
                return ep;
            }
        }
        return null;
    }

}
