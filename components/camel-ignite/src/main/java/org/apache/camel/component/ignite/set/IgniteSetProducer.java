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
package org.apache.camel.component.ignite.set;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.ignite.IgniteSet;

/**
 * The Ignite Set producer.
 */
public class IgniteSetProducer extends DefaultAsyncProducer {

    /**
     * The operations that use the body as the element (or the elements) to add, find or remove.
     */
    private static final Set<IgniteSetOperation> ELEMENT_OPERATIONS = EnumSet.of(IgniteSetOperation.ADD,
            IgniteSetOperation.CONTAINS, IgniteSetOperation.REMOVE, IgniteSetOperation.RETAIN_ALL);

    private IgniteSetEndpoint endpoint;
    private IgniteSet<Object> set;

    public IgniteSetProducer(IgniteSetEndpoint endpoint, IgniteSet<Object> set) {
        super(endpoint);
        this.endpoint = endpoint;
        this.set = set;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message in = exchange.getIn();
        IgniteSetOperation operation = setOperationFor(exchange);

        Object body = in.getBody();
        if (body == null && ELEMENT_OPERATIONS.contains(operation)) {
            exchange.setException(new InvalidPayloadException(
                    exchange, Object.class, in, "the " + operation + " operation needs the element in the message body"));
            callback.done(true);
            return true;
        }

        Message out = exchange.getOut();
        MessageHelper.copyHeaders(exchange.getIn(), out, true);

        switch (operation) {

            case ADD:
                if (Collection.class.isAssignableFrom(body.getClass()) && !endpoint.isTreatCollectionsAsCacheObjects()) {
                    out.setBody(set.addAll((Collection<? extends Object>) body));
                } else {
                    out.setBody(set.add(body));
                }
                break;

            case CONTAINS:
                if (Collection.class.isAssignableFrom(body.getClass()) && !endpoint.isTreatCollectionsAsCacheObjects()) {
                    out.setBody(set.containsAll((Collection<? extends Object>) body));
                } else {
                    out.setBody(set.contains(body));
                }
                break;

            case SIZE:
                out.setBody(set.size());
                break;

            case REMOVE:
                if (Collection.class.isAssignableFrom(body.getClass()) && !endpoint.isTreatCollectionsAsCacheObjects()) {
                    out.setBody(set.removeAll((Collection<? extends Object>) body));
                } else {
                    out.setBody(set.remove(body));
                }
                break;

            case CLEAR:
                if (endpoint.isPropagateIncomingBodyIfNoReturnValue()) {
                    out.setBody(body);
                }
                set.clear();
                break;

            case ITERATOR:
                Iterator<?> iterator = set.iterator();
                out.setBody(iterator);
                break;

            case ARRAY:
                out.setBody(set.toArray());
                break;

            case RETAIN_ALL:
                if (Collection.class.isAssignableFrom(body.getClass())) {
                    out.setBody(set.retainAll((Collection<? extends Object>) body));
                } else {
                    out.setBody(set.retainAll(Collections.singleton(body)));
                }
                break;

            default:
                exchange.setException(new UnsupportedOperationException("Operation not supported by Ignite Set producer."));
                break;
        }

        callback.done(false);
        return false;
    }

    private IgniteSetOperation setOperationFor(Exchange exchange) {
        IgniteSetOperation operation = exchange.getIn().getHeader(IgniteConstants.IGNITE_SETS_OPERATION,
                endpoint.getOperation(), IgniteSetOperation.class);
        if (operation == null) {
            throw new IllegalArgumentException(
                    "No operation specified. Set the operation via the operation option of the endpoint or the header "
                                               + IgniteConstants.IGNITE_SETS_OPERATION);
        }
        return operation;
    }

}
