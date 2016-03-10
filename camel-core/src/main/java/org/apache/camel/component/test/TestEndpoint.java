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
package org.apache.camel.component.test;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.EndpointHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/test.html">Test Endpoint</a> is a
 * <a href="http://camel.apache.org/mock.html">Mock Endpoint</a> for testing but it will
 * pull all messages from the nested endpoint and use those as expected message body assertions.
 *
 * @version 
 */
@UriEndpoint(scheme = "test", title = "Test", syntax = "test:name", producerOnly = true, label = "core,testing", lenientProperties = true)
public class TestEndpoint extends MockEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(TestEndpoint.class);
    private Endpoint expectedMessageEndpoint;
    @UriPath(description = "Name of endpoint to lookup in the registry to use for polling messages used for testing") @Metadata(required = "true")
    private String name;
    @UriParam(label = "producer", defaultValue = "2000")
    private long timeout = 2000L;

    public TestEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public void setExpectedMessageEndpoint(Endpoint expectedMessageEndpoint) {
        this.expectedMessageEndpoint = expectedMessageEndpoint;
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Consuming expected messages from: {}", expectedMessageEndpoint);

        final List<Object> expectedBodies = new ArrayList<Object>();
        EndpointHelper.pollEndpoint(expectedMessageEndpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                // if file based we need to load the file into memory as the file may be deleted/moved afterwards
                Object body = getInBody(exchange);
                if (body instanceof WrappedFile) {
                    body = exchange.getIn().getBody(byte[].class);
                }
                LOG.trace("Received message body {}", body);
                expectedBodies.add(body);
            }
        }, timeout);

        LOG.debug("Received: {} expected message(s) from: {}", expectedBodies.size(), expectedMessageEndpoint);
        expectedBodiesReceived(expectedBodies);
    }

    /**
     * This method allows us to convert or coerce the expected message body into some other type
     */
    protected Object getInBody(Exchange exchange) {
        return exchange.getIn().getBody();
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout to use when polling for message bodies from the URI
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
