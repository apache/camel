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
package org.apache.camel.component.hazelcast.list;

import java.util.Map;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IList;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultProducer;

/**
 * Implementation of Hazelcast List {@link Producer}.
 */
public class HazelcastListProducer extends DefaultProducer {

    private final IList<Object> list;
    private final HazelcastComponentHelper helper = new HazelcastComponentHelper();

    public HazelcastListProducer(Endpoint endpoint, String listName) {
        super(endpoint);
        this.list = Hazelcast.getList(listName);
    }

    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        // get header parameters
        int operation = -1;
        Integer pos = null;

        if (headers.containsKey(HazelcastConstants.OBJECT_POS)) {
            if (!(headers.get(HazelcastConstants.OBJECT_POS) instanceof Integer)) {
                throw new IllegalArgumentException("OBJECT_POS Should be of type Integer");
            }
            pos = (Integer) headers.get(HazelcastConstants.OBJECT_POS);
        }

        if (headers.containsKey(HazelcastConstants.OPERATION)) {
            if (headers.get(HazelcastConstants.OPERATION) instanceof String) {
                operation = helper.lookupOperationNumber((String) headers.get(HazelcastConstants.OPERATION));
            } else {
                operation = (Integer) headers.get(HazelcastConstants.OPERATION);
            }
        }

        switch (operation) {

        case HazelcastConstants.ADD_OPERATION:
            this.add(pos, exchange);
            break;

        case HazelcastConstants.GET_OPERATION:
            this.get(pos, exchange);
            break;

        case HazelcastConstants.SETVALUE_OPERATION:
            this.set(pos, exchange);
            break;

        case HazelcastConstants.REMOVEVALUE_OPERATION:
            this.remove(pos, exchange);
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
            // add the specified element to the end of the list
            list.add(body);
        } else {
            // add the specified element at the specified position
            list.add(pos, body);
        }
    }

    private void get(Integer pos, Exchange exchange) {
        // TODO: this operation is currently not supported by hazelcast
        exchange.getOut().setBody(this.list.get(pos));
    }

    private void set(Integer pos, Exchange exchange) {
        // TODO: this operation is currently not supported by hazelcast
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
            // TODO: this operation is currently not supported by hazelcast
            // removes the element at the specified position
            int position = pos;
            list.remove(position);
        }
    }
}
