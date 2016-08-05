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
package org.apache.camel.component.ignite.set;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.ignite.IgniteSet;

/**
 * The Ignite Set producer.
 */
public class IgniteSetProducer extends DefaultAsyncProducer {

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
        Message out = exchange.getOut();
        MessageHelper.copyHeaders(exchange.getIn(), out, true);

        Object body = in.getBody();

        switch (setOperationFor(exchange)) {

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
            return true;
        }

        return true;
    }

    private IgniteSetOperation setOperationFor(Exchange exchange) {
        return exchange.getIn().getHeader(IgniteConstants.IGNITE_SETS_OPERATION, endpoint.getOperation(), IgniteSetOperation.class);
    }

}
