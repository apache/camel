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
package org.apache.camel.dataformat.beanio;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

import org.beanio.BeanReader;

public class BeanIOIterator implements Iterator<Object>, Closeable {

    private BeanReader reader;
    private transient Object next;

    public BeanIOIterator(BeanReader reader) {
        this.reader = reader;
        this.next = next();
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
            answer = reader.read();
        } else {
            next = reader.read();
        }
        return answer;
    }

    @Override
    public void remove() {
        // noop
    }
}
