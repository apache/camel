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
package org.apache.camel.impl;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;

/**
 * To process the delegated producer in synchronous mode.
 * <p/>
 * This is used to enforce asynchronous producers to run in synchronous mode
 * when it has been configured to do so.
 * <p/>
 * This delegate allows the component developers easily to support their
 * existing asynchronous producer to behave synchronously by wrapping their
 * producer in this synchronous delegate.
 *
 * @version 
 */
public class SynchronousDelegateProducer implements Producer {

    private final Producer producer;

    public SynchronousDelegateProducer(Producer producer) {
        this.producer = producer;
    }

    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    public Exchange createExchange() {
        return producer.createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return producer.createExchange(pattern);
    }

    @Deprecated
    public Exchange createExchange(Exchange exchange) {
        return producer.createExchange(exchange);
    }

    public void process(Exchange exchange) throws Exception {
        producer.process(exchange);
    }

    public void start() throws Exception {
        producer.start();
    }

    public void stop() throws Exception {
        producer.stop();
    }

    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    public String toString() {
        return producer.toString();
    }
}
