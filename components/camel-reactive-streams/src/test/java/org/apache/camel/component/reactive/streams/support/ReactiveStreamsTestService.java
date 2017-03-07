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
package org.apache.camel.component.reactive.streams.support;

import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConsumer;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.api.DispatchCallback;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Test (dummy) service for reactive streams.
 */
public class ReactiveStreamsTestService implements CamelReactiveStreamsService {

    private String name;

    public ReactiveStreamsTestService() {
    }

    public ReactiveStreamsTestService(String name) {
        this.name = name;
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void setCamelContext(CamelContext camelContext) {

    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public CamelContext getCamelContext() {
        return null;
    }

    @Override
    public Publisher<Exchange> fromStream(String name) {
        return null;
    }

    @Override
    public <T> Publisher<T> fromStream(String name, Class<T> type) {
        return null;
    }

    @Override
    public Subscriber<Exchange> streamSubscriber(String name) {
        return null;
    }

    @Override
    public <T> Subscriber<T> streamSubscriber(String name, Class<T> type) {
        return null;
    }

    @Override
    public void sendCamelExchange(String name, Exchange exchange, DispatchCallback<Exchange> callback) {

    }

    @Override
    public void attachCamelConsumer(String name, ReactiveStreamsConsumer consumer) {

    }

    @Override
    public void detachCamelConsumer(String name) {

    }

    @Override
    public void attachCamelProducer(String name, ReactiveStreamsProducer producer) {

    }

    @Override
    public void detachCamelProducer(String name) {

    }

    @Override
    public Publisher<Exchange> toStream(String name, Object data) {
        return null;
    }

    @Override
    public <T> Publisher<T> toStream(String name, Object data, Class<T> type) {
        return null;
    }

    @Override
    public Function<?, ? extends Publisher<Exchange>> toStream(String name) {
        return null;
    }

    @Override
    public <T> Function<Object, Publisher<T>> toStream(String name, Class<T> type) {
        return null;
    }

    @Override
    public Publisher<Exchange> from(String uri) {
        return null;
    }

    @Override
    public <T> Publisher<T> from(String uri, Class<T> type) {
        return null;
    }

    @Override
    public Publisher<Exchange> to(String uri, Object data) {
        return null;
    }

    @Override
    public Function<Object, Publisher<Exchange>> to(String uri) {
        return null;
    }

    @Override
    public <T> Publisher<T> to(String uri, Object data, Class<T> type) {
        return null;
    }

    @Override
    public <T> Function<Object, Publisher<T>> to(String uri, Class<T> type) {
        return null;
    }

    @Override
    public void process(String uri, Function<? super Publisher<Exchange>, ?> processor) {

    }

    @Override
    public <T> void process(String uri, Class<T> type, Function<? super Publisher<T>, ?> processor) {

    }

    @Override
    public Subscriber<Exchange> subscriber(String uri) {
        return null;
    }

    @Override
    public <T> Subscriber<T> subscriber(String uri, Class<T> type) {
        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
