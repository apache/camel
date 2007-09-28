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
package org.apache.camel.component.cxf;

import org.apache.camel.*;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.CamelContextHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;


/**
 * Defines the <a href="http://activemq.apache.org/camel/cxf.html">CXF Component</a>

 * @version $Revision: 576522 $
 */
public class CxfSoapComponent extends DefaultComponent {

    private static final Log LOG = LogFactory.getLog(CxfSoapComponent.class);

    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        String name = uri.substring(uri.indexOf(':') + 1);
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(getCamelContext(), name);
        return new SoapEndpoint(endpoint);
    }

    protected void processSoapConsumerIn(Exchange exchange) throws Exception {
        LOG.info("processSoapConsumerIn: " + exchange);
        // TODO
    }

    protected void processSoapConsumerOut(Exchange exchange) throws Exception {
        LOG.info("processSoapConsumerOut: " + exchange);
        // TODO
    }

    protected void processSoapProviderIn(Exchange exchange) throws Exception {
        LOG.info("processSoapProviderIn: " + exchange);
        // TODO
    }

    protected void processSoapProviderOut(Exchange exchange) throws Exception {
        LOG.info("processSoapProviderOut: " + exchange);
        // TODO
    }

    public class SoapEndpoint implements Endpoint {

        private final Endpoint endpoint;

        public SoapEndpoint(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        public boolean isSingleton() {
            return endpoint.isSingleton();
        }

        public String getEndpointUri() {
            return endpoint.getEndpointUri();
        }

        public Exchange createExchange() {
            return endpoint.createExchange();
        }

        public Exchange createExchange(ExchangePattern pattern) {
            return endpoint.createExchange(pattern);
        }

        public Exchange createExchange(Exchange exchange) {
            return endpoint.createExchange(exchange);
        }

        public CamelContext getContext() {
            return endpoint.getContext();
        }

        public Producer createProducer() throws Exception {
            Producer producer = endpoint.createProducer();
            return new SoapProducer(producer);
        }

        public Consumer createConsumer(Processor processor) throws Exception {
            Processor soapProcessor = new AsyncProcessorDecorator(processor,
                    new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            processSoapConsumerIn(exchange);
                        }
                    },
                    new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            processSoapConsumerOut(exchange);
                        }
                    });
            return  endpoint.createConsumer(soapProcessor);
        }

        public PollingConsumer createPollingConsumer() throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    public class SoapProducer implements Producer, AsyncProcessor {

        private final Producer producer;
        private final AsyncProcessor processor;

        public SoapProducer(Producer producer) {
            this.producer = producer;
            this.processor = new AsyncProcessorDecorator(producer,
                    new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            processSoapProviderIn(exchange);
                        }
                    },
                    new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            processSoapProviderOut(exchange);
                        }
                    });
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

        public Exchange createExchange(Exchange exchange) {
            return producer.createExchange(exchange);
        }

        public void process(Exchange exchange) throws Exception {
            AsyncProcessorHelper.process(this, exchange);
        }

        public boolean process(Exchange exchange, AsyncCallback callback) {
            return processor.process(exchange, callback);
        }

        public void start() throws Exception {
            producer.start();
        }

        public void stop() throws Exception {
            producer.stop();
        }
    }

    public static class AsyncProcessorDecorator implements AsyncProcessor {

        private final AsyncProcessor processor;
        private final Processor before;
        private final Processor after;

        public AsyncProcessorDecorator(Processor processor, Processor before, Processor after) {
            this.processor = AsyncProcessorTypeConverter.convert(processor);
            this.before = before;
            this.after = after;
        }

        public void process(Exchange exchange) throws Exception {
            AsyncProcessorHelper.process(this, exchange);
        }

        public boolean process(final Exchange exchange, final AsyncCallback callback) {
            try {
                before.process(exchange);
            } catch (Throwable t) {
                exchange.setException(t);
                callback.done(true);
                return true;
            }
            return processor.process(exchange, new AsyncCallback() {
                public void done(boolean doneSynchronously) {
                    try {
                        after.process(exchange);
                        callback.done(doneSynchronously);
                    } catch (Throwable t) {
                        exchange.setException(t);
                    }
                }
            });
        }

    }

}
