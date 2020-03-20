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
package org.apache.camel.component.hazelcast.list;

import java.util.Collection;
import java.util.Map;

import com.hazelcast.collection.IList;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;

/**
 * Implementation of Hazelcast List {@link Producer}.
 */
public class HazelcastListProducer extends HazelcastDefaultProducer {

    private final IList<Object> list;

    public HazelcastListProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String listName) {
        super(endpoint);
        this.list = hazelcastInstance.getList(listName);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        // GET header parameters
        Integer pos = null;

        if (headers.containsKey(HazelcastConstants.OBJECT_POS)) {
            if (!(headers.get(HazelcastConstants.OBJECT_POS) instanceof Integer)) {
                throw new IllegalArgumentException("OBJECT_POS Should be of type Integer");
            }
            pos = (Integer) headers.get(HazelcastConstants.OBJECT_POS);
        }

        final HazelcastOperation operation = lookupOperation(exchange);

        switch (operation) {

            case ADD:
                this.add(pos, exchange);
                break;

            case GET:
                this.get(pos, exchange);
                break;

            case SET_VALUE:
                this.set(pos, exchange);
                break;

            case REMOVE_VALUE:
                this.remove(pos, exchange);
                break;

            case CLEAR:
                this.clear();
                break;

            case ADD_ALL:
                this.addAll(pos, exchange);
                break;

            case REMOVE_ALL:
                this.removeAll(exchange);
                break;

            case RETAIN_ALL:
                this.retainAll(exchange);
                break;

            default:
                throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the LIST cache.", operation, HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

    private void add(Integer pos, Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        if (null == pos) {
            // ADD the specified element to the end of the list
            list.add(body);
        } else {
            // ADD the specified element at the specified position
            list.add(pos, body);
        }
    }

    private void get(Integer pos, Exchange exchange) {
        exchange.getOut().setBody(this.list.get(pos));
    }

    private void set(Integer pos, Exchange exchange) {
        if (null == pos) {
            throw new IllegalArgumentException("Empty position for set operation.");
        } else {
            final Object body = exchange.getIn().getBody();
            list.set(pos, body);
        }
    }

    private void remove(Integer pos, Exchange exchange) {
        if (null == pos) {
            // removes the first occurrence in the list
            final Object body = exchange.getIn().getBody();
            list.remove(body);
        } else {
            // removes the element at the specified position
            int position = pos;
            list.remove(position);
        }
    }

    private void clear() {
        list.clear();
    }

    private void addAll(Integer pos, Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        if (null == pos) {
            list.addAll((Collection<? extends Object>) body);
        } else {
            list.addAll(pos, (Collection<? extends Object>) body);
        }
    }

    private void removeAll(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        list.removeAll((Collection<?>) body);
    }

    private void retainAll(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        list.retainAll((Collection<?>) body);
    }
}
