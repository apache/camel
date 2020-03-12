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
package org.apache.camel.component.hazelcast.set;

import java.util.Collection;

import com.hazelcast.collection.ISet;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;

/**
 * Implementation of Hazelcast Set {@link Producer}.
 */
public class HazelcastSetProducer extends HazelcastDefaultProducer {

    private final ISet<Object> set;

    public HazelcastSetProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String setName) {
        super(endpoint);
        this.set = hazelcastInstance.getSet(setName);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        final HazelcastOperation operation = lookupOperation(exchange);

        switch (operation) {

            case ADD:
                this.add(exchange);
                break;

            case REMOVE_VALUE:
                this.remove(exchange);
                break;

            case CLEAR:
                this.clear();
                break;

            case ADD_ALL:
                this.addAll(exchange);
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

    private void add(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        set.add(body);
    }

    private void remove(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        set.remove(body);
    }

    private void clear() {
        set.clear();
    }

    private void addAll(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        set.addAll((Collection<? extends Object>) body);
    }

    private void removeAll(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        set.removeAll((Collection<?>) body);
    }

    private void retainAll(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        set.retainAll((Collection<?>) body);
    }
}
