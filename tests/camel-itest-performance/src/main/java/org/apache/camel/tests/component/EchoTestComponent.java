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
package org.apache.camel.tests.component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;

public class EchoTestComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new EchoEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    private final class EchoEndpoint extends DefaultEndpoint {
        protected EchoEndpoint(String uri, Component component) {
            super(uri, component);
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            // Component only supports Producers
            return null;
        }

        @Override
        public Producer createProducer() throws Exception {
            return new EchoProducer(this);
        }

        @Override
        public boolean isSingleton() {
            return false;
        }
    }

    private final class EchoProducer extends DefaultProducer implements AsyncProcessor {
        protected EchoProducer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            // do nothing, echo is implicit
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            // do nothing, echo is implicit
            return true;
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
