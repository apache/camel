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
package org.apache.camel.component.mtconnect;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/** Collects what a route receives: each message's body and a copy of its headers, in arrival order. */
final class ReceivedMessages implements Processor {

    /** One received message. */
    record Received(Object body, Map<String, Object> headers) {

        Object header(String name) {
            return headers.get(name);
        }

        String text(String name) {
            Object value = headers.get(name);
            return value == null ? null : value.toString();
        }

        Long sequence() {
            return (Long) headers.get(MTConnectConstants.SEQUENCE);
        }

        String dataItemId() {
            return text(MTConnectConstants.DATA_ITEM_ID);
        }

        boolean good() {
            return MTConnectConstants.QUALITY_GOOD.equals(headers.get(MTConnectConstants.QUALITY));
        }

        boolean bad() {
            return MTConnectConstants.QUALITY_BAD.equals(headers.get(MTConnectConstants.QUALITY));
        }

        boolean sample() {
            return MTConnectConstants.DOCUMENT_SAMPLE.equals(headers.get(MTConnectConstants.DOCUMENT));
        }

        boolean current() {
            return MTConnectConstants.DOCUMENT_CURRENT.equals(headers.get(MTConnectConstants.DOCUMENT));
        }
    }

    private final List<Received> messages = new CopyOnWriteArrayList<>();

    @Override
    public void process(Exchange exchange) {
        messages.add(new Received(exchange.getMessage().getBody(), Map.copyOf(exchange.getMessage().getHeaders())));
    }

    List<Received> all() {
        return List.copyOf(messages);
    }

    List<Received> matching(Predicate<Received> predicate) {
        return messages.stream().filter(predicate).toList();
    }

    /** The messages that carry an observation of the given data item. */
    List<Received> of(String dataItemId) {
        return matching(received -> dataItemId.equals(received.dataItemId()));
    }

    int size() {
        return messages.size();
    }
}
