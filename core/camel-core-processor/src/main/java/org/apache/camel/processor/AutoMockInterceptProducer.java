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

package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.spi.AutoMockInterceptStrategy;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a producer that can be intercepted when sending to an endpoint, by sending the exchange to a corresponding
 * mock endpoints, which is used by auto mocking endpoints for testing purpose.
 */
public class AutoMockInterceptProducer extends DelegateAsyncProcessor implements AsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AutoMockInterceptProducer.class);

    private final AsyncProducer producer;
    private final CamelContext camelContext;
    private Producer mockProducer;

    public AutoMockInterceptProducer(Producer producer) {
        this(AsyncProcessorConverterHelper.convert(producer));
    }

    public AutoMockInterceptProducer(AsyncProducer producer) {
        super(producer);
        this.producer = producer;
        this.camelContext = producer.getEndpoint().getCamelContext();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (mockProducer == null) {
            return super.process(exchange, callback);
        }

        boolean noMatched = true;
        boolean mock = false;
        boolean skip = false;
        try {
            for (AutoMockInterceptStrategy strategy :
                    camelContext.getCamelContextExtension().getAutoMockInterceptStrategies()) {
                if (matchPattern(getEndpoint(), strategy.getPattern())) {
                    noMatched = false;
                    mock = true;
                    skip |= strategy.isSkip(); // skip if anyone matched the pattern
                }
            }
            if (mock) {
                // send to mock endpoint
                LOG.debug("Auto mocked: {} (uri: {} skip: {})", exchange, getEndpoint(), skip);
                mockProducer.process(exchange);
            }
            if (noMatched || !skip) {
                // send to original endpoint
                return super.process(exchange, callback);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    @Override
    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    @Override
    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        // skip mock endpoints
        if (!producer.getEndpoint().getEndpointUri().startsWith("mock:")) {
            mockProducer = createProducer(producer.getEndpoint());
        }
        ServiceHelper.buildService(mockProducer);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ServiceHelper.initService(mockProducer);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(mockProducer);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(mockProducer);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        ServiceHelper.stopAndShutdownServices(mockProducer);
    }

    private static boolean matchPattern(Endpoint endpoint, String pattern) {
        return pattern == null
                || EndpointHelper.matchEndpoint(endpoint.getCamelContext(), endpoint.getEndpointUri(), pattern);
    }

    private static Producer createProducer(Endpoint endpoint) throws Exception {
        // create mock endpoint which we will use as interceptor
        // replace :// from scheme to make it easy to look up the mock endpoint without having double :// in uri
        String key = "mock:" + endpoint.getEndpointKey().replaceFirst("://", ":");
        // strip off parameters as well
        if (key.contains("?")) {
            key = StringHelper.before(key, "?");
        }
        Endpoint mock = endpoint.getCamelContext().getEndpoint(key, Endpoint.class);
        return mock.createProducer();
    }
}
