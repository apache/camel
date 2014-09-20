/**
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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Scanner;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;

/**
 * Group based {@link Iterator} which groups the given {@link Iterator} a number of times
 * and then return a combined response as a String.
 * <p/>
 * This implementation uses as internal byte array buffer, to combine the response.
 * The token is inserted between the individual parts.
 * <p/>
 * For example if you group by new line, then a new line token is inserted between the lines.
 */
public final class GroupIterator implements Iterator<Object>, Closeable {

    private final CamelContext camelContext;
    private final Iterator<?> it;
    private final String token;
    private final int group;
    private boolean closed;
    private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

    /**
     * Creates a new group iterator
     *
     * @param camelContext  the camel context
     * @param it            the iterator to group
     * @param token         then token used to separate between the parts, use <tt>null</tt> to not add the token
     * @param group         number of parts to group together
     * @throws IllegalArgumentException is thrown if group is not a positive number
     */
    public GroupIterator(CamelContext camelContext, Iterator<?> it, String token, int group) {
        this.camelContext = camelContext;
        this.it = it;
        this.token = token;
        this.group = group;
        if (group <= 0) {
            throw new IllegalArgumentException("Group must be a positive number, was: " + group);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (it instanceof Scanner) {
                // special for Scanner which implement the Closeable since JDK7 
                Scanner scanner = (Scanner) it;
                scanner.close();
                IOException ioException = scanner.ioException();
                if (ioException != null) {
                    throw ioException;
                }
            } else if (it instanceof Closeable) {
                IOHelper.closeWithException((Closeable) it);
            }
        } finally {
            // close the buffer as well
            bos.close();
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
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    private Object doNext() throws IOException, NoTypeConversionAvailableException {
        int count = 0;
        Object data = "";
        while (count < group && it.hasNext()) {
            data = it.next();

            // include token in between
            if (data != null && count > 0 && token != null) {
                bos.write(token.getBytes());
            }
            if (data instanceof InputStream) {
                InputStream is = (InputStream) data;
                IOHelper.copy(is, bos);
            } else if (data instanceof byte[]) {
                byte[] bytes = (byte[]) data;
                bos.write(bytes);
            } else if (data != null) {
                // convert to input stream
                InputStream is = camelContext.getTypeConverter().mandatoryConvertTo(InputStream.class, data);
                IOHelper.copy(is, bos);
            }

            count++;
        }

        // prepare and return answer as String
        String answer = bos.toString();
        bos.reset();
        return answer;
    }

    @Override
    public void remove() {
        it.remove();
    }
}
