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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;

/**
 * Group based {@link Iterator} which groups the given {@link Iterator} a number of times and then return a combined
 * response as a <tt>List</tt>.
 * <p/>
 * This implementation uses a internal array list, to combine the response.
 *
 * @see GroupTokenIterator
 */
public final class GroupIterator implements Iterator<Object>, Closeable {

    private final Iterator<?> it;
    private final int group;
    private boolean closed;

    /**
     * Creates a new group iterator
     *
     * @param  it                       the iterator to group
     * @param  group                    number of parts to group together
     * @throws IllegalArgumentException is thrown if group is not a positive number
     */
    public GroupIterator(Iterator<?> it, int group) {
        this.it = it;
        this.group = group;
        if (group <= 0) {
            throw new IllegalArgumentException("Group must be a positive number, was: " + group);
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
        try {
            return doNext();
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    private Object doNext() {
        List<Object> list = new ArrayList<>();
        int count = 0;
        while (count < group && it.hasNext()) {
            Object data = it.next();
            list.add(data);
            count++;
        }

        return list;
    }

    @Override
    public void remove() {
        it.remove();
    }
}
