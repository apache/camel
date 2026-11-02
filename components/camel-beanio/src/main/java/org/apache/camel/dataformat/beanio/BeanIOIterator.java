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
package org.apache.camel.dataformat.beanio;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import org.beanio.BeanReader;

public class BeanIOIterator implements Iterator<Object>, Closeable {

    private BeanReader reader;
    private transient Object next;
    // the results that an error handler added (handleErrorAndAddAsResult) while the reader was reading, in their order:
    // the reader goes on after a record it handled, so more than one can come before the record that read() returns
    private final Deque<Object> forced = new ArrayDeque<>();

    public BeanIOIterator(BeanReader reader) {
        this(reader, true);
    }

    /**
     * @param reader    the reader
     * @param readFirst whether to read the first record now, else {@link #readFirst()} must be called before the
     *                  iterator is used (such as once the error handler is set on the reader)
     */
    BeanIOIterator(BeanReader reader, boolean readFirst) {
        this.reader = reader;
        if (readFirst) {
            readFirst();
        }
    }

    /**
     * Reads the first record.
     */
    void readFirst() {
        this.next = readNext();
    }

    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Object next() {
        Object answer = next;
        if (answer == null) {
            answer = readNext();
        } else {
            next = readNext();
        }
        return answer;
    }

    private Object readNext() {
        if (!forced.isEmpty()) {
            return forced.poll();
        }
        Object answer = reader.read();
        if (!forced.isEmpty()) {
            // the error handler added results for the records it handled during this read, which come before the
            // record that the read returned (the same order as the data format, which adds them to its list)
            if (answer != null) {
                forced.add(answer);
            }
            answer = forced.poll();
        }
        return answer;
    }

    @Override
    public void remove() {
        // noop
    }

    /**
     * Adds a custom object as the next result, such as from a custom error handler. Several objects added during one
     * read are returned in the order they were added, before the record that the read returned. As the reader goes on
     * after each record that the error handler handled, the results of handled records in a row are kept until the
     * reader returns the next record. A {@code null} is ignored.
     */
    public void setNext(Object next) { // NOSONAR
        if (next != null) {
            forced.add(next);
        }
    }
}
