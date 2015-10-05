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
package org.apache.camel.component.hazelcast.set;

import java.util.Collection;
import java.util.Map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.core.ISet;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;

/**
 * Implementation of Hazelcast Set {@link Producer}.
 */
public class HazelcastSetProducer extends HazelcastDefaultProducer {

    private final ISet<Object> set;

    public HazelcastSetProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String setName) {
        super(endpoint);
        this.set = hazelcastInstance.getSet(setName);
    }

    public void process(Exchange exchange) throws Exception {

        final int operation = lookupOperationNumber(exchange);

        switch (operation) {

        case HazelcastConstants.ADD_OPERATION:
            this.add(exchange);
            break;

        case HazelcastConstants.REMOVEVALUE_OPERATION:
            this.remove(exchange);
            break;
            
        case HazelcastConstants.CLEAR_OPERATION:
            this.clear();
            break;
            
        case HazelcastConstants.ADD_ALL_OPERATION:
            this.addAll(exchange);
            break;
            
        case HazelcastConstants.REMOVE_ALL_OPERATION:
            this.removeAll(exchange);
            break;

        case HazelcastConstants.RETAIN_ALL_OPERATION:
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
