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
package org.apache.camel.component.ignite.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.ignite.IgniteQueue;

public class IgniteQueueProducer extends DefaultAsyncProducer {

    private IgniteQueueEndpoint endpoint;
    private IgniteQueue<Object> queue;

    public IgniteQueueProducer(IgniteQueueEndpoint endpoint, IgniteQueue<Object> queue) {
        super(endpoint);
        this.endpoint = endpoint;
        this.queue = queue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message in = exchange.getIn();
        Message out = exchange.getOut();
        MessageHelper.copyHeaders(exchange.getIn(), out, true);

        Object body = in.getBody();
        Long millis;

        switch (queueOperationFor(exchange)) {

        case ADD:
            if (Collection.class.isAssignableFrom(body.getClass()) && !endpoint.isTreatCollectionsAsCacheObjects()) {
                out.setBody(queue.addAll((Collection<? extends Object>) body));
            } else {
                out.setBody(queue.add(body));
            }
            break;

        case CONTAINS:
            if (Collection.class.isAssignableFrom(body.getClass()) && !endpoint.isTreatCollectionsAsCacheObjects()) {
                out.setBody(queue.containsAll((Collection<? extends Object>) body));
            } else {
                out.setBody(queue.contains(body));
            }
            break;

        case SIZE:
            out.setBody(queue.size());
            break;

        case REMOVE:
            if (Collection.class.isAssignableFrom(body.getClass()) && !endpoint.isTreatCollectionsAsCacheObjects()) {
                out.setBody(queue.removeAll((Collection<? extends Object>) body));
            } else {
                out.setBody(queue.remove(body));
            }
            break;

        case CLEAR:
            if (endpoint.isPropagateIncomingBodyIfNoReturnValue()) {
                out.setBody(body);
            }
            queue.clear();
            break;

        case ITERATOR:
            Iterator<?> iterator = queue.iterator();
            out.setBody(iterator);
            break;

        case ARRAY:
            out.setBody(queue.toArray());
            break;

        case RETAIN_ALL:
            if (Collection.class.isAssignableFrom(body.getClass())) {
                out.setBody(queue.retainAll((Collection<? extends Object>) body));
            } else {
                out.setBody(queue.retainAll(Collections.singleton(body)));
            }
            break;

        case DRAIN:
            Integer maxElements = in.getHeader(IgniteConstants.IGNITE_QUEUE_MAX_ELEMENTS, Integer.class);

            Collection<Object> col = null;
            if (body != null && Collection.class.isAssignableFrom(body.getClass())) {
                col = (Collection<Object>) body;
            } else {
                col = maxElements != null ? new ArrayList<>(maxElements) : new ArrayList<>();
            }

            int transferred = -1;
            if (maxElements == null) {
                transferred = queue.drainTo(col);
            } else {
                transferred = queue.drainTo(col, maxElements);
            }
            out.setBody(col);
            out.setHeader(IgniteConstants.IGNITE_QUEUE_TRANSFERRED_COUNT, transferred);
            break;

        case ELEMENT:
            out.setBody(queue.element());
            break;

        case OFFER:
            millis = in.getHeader(IgniteConstants.IGNITE_QUEUE_TIMEOUT_MILLIS, endpoint.getTimeoutMillis(), Long.class);
            boolean result = millis == null ? queue.offer(body) : queue.offer(body, millis, TimeUnit.MILLISECONDS);
            out.setBody(result);
            break;

        case PEEK:
            out.setBody(queue.peek());
            break;

        case POLL:
            millis = in.getHeader(IgniteConstants.IGNITE_QUEUE_TIMEOUT_MILLIS, endpoint.getTimeoutMillis(), Long.class);
            out.setBody(millis == null ? queue.poll() : queue.poll(millis, TimeUnit.MILLISECONDS));
            break;

        case PUT:
            if (endpoint.isPropagateIncomingBodyIfNoReturnValue()) {
                out.setBody(in.getBody());
            }
            queue.put(body);
            break;

        case TAKE:
            out.setBody(queue.take());
            break;
            
        default:
            exchange.setException(new UnsupportedOperationException("Operation not supported by Ignite Queue producer."));
            return true;
        }

        return true;
    }

    private IgniteQueueOperation queueOperationFor(Exchange exchange) {
        return exchange.getIn().getHeader(IgniteConstants.IGNITE_QUEUE_OPERATION, endpoint.getOperation(), IgniteQueueOperation.class);
    }

}
