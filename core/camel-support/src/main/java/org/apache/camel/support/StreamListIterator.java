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

import org.apache.camel.RuntimeCamelException;

/**
 * Iterator that applies a {@link RowMapper} transformation to each element lazily. Useful for memory-efficient
 * processing of large result sets from databases or APIs.
 * <p>
 * When mapping a row fails, that row has already been taken from the delegate, so the iterator keeps failing instead of
 * going on with the next row (whoever reads the iterator again, such as a redelivered split, gets an error and does not
 * skip the row).
 *
 * @param <T> the input type from the delegate iterator
 * @param <E> the output type after applying the row mapper
 */
public final class StreamListIterator<T, E> implements Iterator<E> {

    private final RowMapper<T, E> rowMapper;
    private final Iterator<T> delegate;
    // set when mapping a row has failed: the row is consumed from the delegate, so going on would skip it
    private volatile RuntimeException failure;

    public StreamListIterator(RowMapper<T, E> rowMapper, Iterator<T> delegate) {
        this.rowMapper = rowMapper;
        this.delegate = delegate;
    }

    /**
     * Whether there are more rows.
     *
     * @throws RuntimeCamelException when mapping a row has failed before, with that failure as cause
     */
    @Override
    public boolean hasNext() {
        checkFailure();
        return delegate.hasNext();
    }

    /**
     * The next row, mapped with the row mapper.
     *
     * @throws RuntimeCamelException when mapping a row has failed before, with that failure as cause (the first time,
     *                               the exception of the row mapper is thrown as it is)
     */
    @Override
    public E next() {
        checkFailure();
        T row = delegate.next();
        try {
            return rowMapper.map(row);
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        }
    }

    private void checkFailure() {
        RuntimeException cause = failure;
        if (cause != null) {
            throw new RuntimeCamelException("Cannot process result: mapping a row failed before", cause);
        }
    }

    @Override
    public void remove() {
        delegate.remove();
    }
}
