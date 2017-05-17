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
package org.apache.camel.component.reactive.streams.springboot.test.support;

import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.ReactiveStreamsCamelSubscriber;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConsumer;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public class ReactiveStreamsServiceTestSupport implements CamelReactiveStreamsService {

    private String id;

    public ReactiveStreamsServiceTestSupport(String id) {
        this.id = id;
    }

    @Override
    public Publisher<Exchange> fromStream(String s) {
        return null;
    }

    @Override
    public <T> Publisher<T> fromStream(String s, Class<T> aClass) {
        return null;
    }

    @Override
    public Subscriber<Exchange> streamSubscriber(String s) {
        return null;
    }

    @Override
    public <T> Subscriber<T> streamSubscriber(String s, Class<T> aClass) {
        return null;
    }

    @Override
    public Publisher<Exchange> toStream(String s, Object o) {
        return null;
    }

    @Override
    public Function<?, ? extends Publisher<Exchange>> toStream(String s) {
        return null;
    }

    @Override
    public <T> Publisher<T> toStream(String s, Object o, Class<T> aClass) {
        return null;
    }

    @Override
    public <T> Function<Object, Publisher<T>> toStream(String s, Class<T> aClass) {
        return null;
    }

    @Override
    public Publisher<Exchange> from(String s) {
        return null;
    }

    @Override
    public <T> Publisher<T> from(String s, Class<T> aClass) {
        return null;
    }

    @Override
    public Subscriber<Exchange> subscriber(String s) {
        return null;
    }

    @Override
    public <T> Subscriber<T> subscriber(String s, Class<T> aClass) {
        return null;
    }

    @Override
    public Publisher<Exchange> to(String s, Object o) {
        return null;
    }

    @Override
    public Function<Object, Publisher<Exchange>> to(String s) {
        return null;
    }

    @Override
    public <T> Publisher<T> to(String s, Object o, Class<T> aClass) {
        return null;
    }

    @Override
    public <T> Function<Object, Publisher<T>> to(String s, Class<T> aClass) {
        return null;
    }

    @Override
    public void process(String s, Function<? super Publisher<Exchange>, ?> function) {

    }

    @Override
    public <T> void process(String s, Class<T> aClass, Function<? super Publisher<T>, ?> function) {

    }

    @Override
    public void attachCamelProducer(String s, ReactiveStreamsProducer producer) {

    }

    @Override
    public void detachCamelProducer(String s) {

    }

    @Override
    public void sendCamelExchange(String s, Exchange exchange) {

    }

    @Override
    public ReactiveStreamsCamelSubscriber attachCamelConsumer(String s, ReactiveStreamsConsumer consumer) {
        return null;
    }

    @Override
    public void detachCamelConsumer(String s) {

    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public String getId() {
        return id;
    }
}
