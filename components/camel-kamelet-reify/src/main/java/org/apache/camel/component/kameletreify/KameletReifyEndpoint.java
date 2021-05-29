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
package org.apache.camel.component.kameletreify;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;

@UriEndpoint(firstVersion = "3.6.0",
             scheme = "kamelet-reify",
             syntax = "kamelet-reify:delegateUri",
             title = "Kamelet Reify",
             lenientProperties = true,
             category = Category.CORE)
public class KameletReifyEndpoint extends DefaultEndpoint implements DelegateEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The delegated uri")
    private final String delegateUri;

    private final Endpoint delegateEndpoint;

    public KameletReifyEndpoint(String uri, KameletReifyComponent component, String delegateUri) {
        super(uri, component);

        this.delegateUri = delegateUri;
        this.delegateEndpoint = component.getCamelContext().getEndpoint(delegateUri);
    }

    public String getDelegateUri() {
        return delegateUri;
    }

    @Override
    public Endpoint getEndpoint() {
        return delegateEndpoint;
    }

    @Override
    public KameletReifyComponent getComponent() {
        return (KameletReifyComponent) super.getComponent();
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KameletProducer();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new KemeletConsumer(processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(delegateEndpoint);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(delegateEndpoint);
        super.doStart();
    }

    // *********************************
    //
    // Helpers
    //
    // *********************************

    private class KemeletConsumer extends DefaultConsumer {
        private volatile Consumer consumer;

        public KemeletConsumer(Processor processor) {
            super(KameletReifyEndpoint.this, processor);
        }

        @Override
        protected void doStart() throws Exception {
            consumer = delegateEndpoint.createConsumer(getProcessor());

            ServiceHelper.startService(consumer);
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopService(consumer);
            super.doStop();
        }
    }

    private class KameletProducer extends DefaultAsyncProducer {
        private volatile AsyncProducer producer;

        public KameletProducer() {
            super(KameletReifyEndpoint.this);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            if (producer != null) {
                return producer.process(exchange, callback);
            } else {
                callback.done(true);
                return true;
            }
        }

        @Override
        protected void doStart() throws Exception {
            producer = delegateEndpoint.createAsyncProducer();
            ServiceHelper.startService(producer);
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopService(producer);
            super.doStop();
        }
    }
}
