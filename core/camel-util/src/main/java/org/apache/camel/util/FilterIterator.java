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

    private final Iterator<T> it;
    private final Predicate<T> filter;
    private T next;
    private boolean closed;
    // true if remove() may be called to remove the element last returned by next()
    private boolean canRemove;

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
            // looking ahead advances the underlying iterator past the element returned by next()
            canRemove = false;
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
            canRemove = true;
            return ep;
        }
        throw new NoSuchElementException();
    }

    /**
     * Removes the last element returned by {@link #next()} from the underlying iterator.
     *
     * @throws IllegalStateException if {@link #next()} has not been called, if {@link #remove()} has already been
     *                               called after the last call to {@link #next()}, if {@link #hasNext()} was called
     *                               after it, or if a later {@link #next()} failed after it looked at further elements
     *                               (they were all rejected or null, or the filter or the underlying iterator threw)
     */
    @Override
    public void remove() {
        if (!canRemove) {
            throw new IllegalStateException("remove() is only supported directly after next()");
        }
        canRemove = false;
        it.remove();
    }

    protected T checkNext() {
        while (!closed && it.hasNext()) {
            // moving the underlying iterator past the element returned by next() means it can no longer be removed,
            // also when no accepted element follows (next() then throws NoSuchElementException) or the filter throws
            canRemove = false;
            T ep = it.next();
            if (ep != null && (filter == null || filter.test(ep))) {
                return ep;
            }
        }
        return null;
    }

}
