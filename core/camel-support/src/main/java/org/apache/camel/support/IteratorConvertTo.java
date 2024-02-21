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
package org.apache.camel.support;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.IOHelper;

/**
 * Wraps an {@link Iterator} so its returned values are automatically converted to a given type.
 */
public final class IteratorConvertTo implements Iterator<Object>, Closeable {

    private final Exchange exchange;
    private final TypeConverter converter;
    private final Iterator<?> it;
    private final Class<?> type;
    private boolean closed;

    /**
     * Creates the convert iterator.
     *
     * @param exchange the exchange
     * @param it       the iterator to wrap
     * @param type     the type to convert to
     */
    public IteratorConvertTo(Exchange exchange, Iterator<?> it, Class<?> type) {
        this.exchange = exchange;
        this.converter = exchange.getContext().getTypeConverter();
        this.it = it;
        this.type = type;
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
        Object next = it.next();
        if (next != null) {
            next = converter.convertTo(type, exchange, next);
        }
        return next;
    }

    @Override
    public void remove() {
        it.remove();
    }
}
