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

import java.util.Iterator;

/**
 * Iterator that applies a {@link RowMapper} transformation to each element lazily. Useful for memory-efficient
 * processing of large result sets from databases or APIs.
 *
 * @param <T> the input type from the delegate iterator
 * @param <E> the output type after applying the row mapper
 */
public final class StreamListIterator<T, E> implements Iterator<E> {

    private final RowMapper<T, E> rowMapper;
    private final Iterator<T> delegate;

    public StreamListIterator(RowMapper<T, E> rowMapper, Iterator<T> delegate) {
        this.rowMapper = rowMapper;
        this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public E next() {
        T row = delegate.next();
        return rowMapper.map(row);
    }

    @Override
    public void remove() {
        delegate.remove();
    }
}
