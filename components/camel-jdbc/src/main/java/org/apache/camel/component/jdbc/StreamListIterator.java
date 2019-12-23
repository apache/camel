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
package org.apache.camel.component.jdbc;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;

import static org.apache.camel.component.jdbc.JdbcHelper.newBeanInstance;

public final class StreamListIterator implements Iterator {

    private final CamelContext camelContext;
    private final String outputClass;
    private final BeanRowMapper beanRowMapper;
    private final Iterator delegate;

    public StreamListIterator(CamelContext camelContext, String outputClass,
                              BeanRowMapper beanRowMapper, Iterator delegate) {
        this.camelContext = camelContext;
        this.outputClass = outputClass;
        this.beanRowMapper = beanRowMapper;
        this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public Object next() {
        Object answer;
        Map row = (Map) delegate.next();
        if (row != null && outputClass != null) {
            try {
                answer = newBeanInstance(camelContext, outputClass, beanRowMapper, row);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            answer = row;
        }
        return answer;
    }

    @Override
    public void remove() {
        delegate.remove();
    }

    @Override
    public void forEachRemaining(Consumer action) {
        delegate.forEachRemaining(action);
    }
}
