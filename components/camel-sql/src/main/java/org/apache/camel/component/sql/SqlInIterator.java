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
package org.apache.camel.component.sql;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Iterator used for SQL IN query.
 * <p/>
 * This ensures we know the parameters is an IN parameter and the values are dynamic and must be
 * set using this iterator.
 */
public class SqlInIterator implements Iterator {

    private final Iterator it;

    public SqlInIterator(Iterator it) {
        this.it = it;
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public Object next() {
        return it.next();
    }

    @Override
    public void remove() {
        it.remove();
    }

    // This method should not have @Override as its a new method in Java 1.8
    // and we need to compile for Java 1.7 also. TODO: enable again in Camel 2.18 onwards
    // @Override
    @SuppressWarnings("unchecked")
    public void forEachRemaining(Consumer action) {
        it.forEachRemaining(action);
    }
}
