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
import org.apache.camel.Service;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.EndpointHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A <a href="http://activemq.apache.org/camel/test.html">Test Endpoint</a> is a
 * <a href="http://activemq.apache.org/camel/mock.html">Mock Endpoint</a> for testing but it will
 * pull all messages from the nested endpoint and use those as expected message body assertions.
 *
 * @version $Revision$
 */
public class TestEndpoint extends MockEndpoint implements Service {
    private static final transient Log LOG = LogFactory.getLog(TestEndpoint.class);
    private final Endpoint expectedMessageEndpoint;
    private long timeout = 2000L;

    public TestEndpoint(String endpointUri, Component component, Endpoint expectedMessageEndpoint) {
        super(endpointUri, component);
        this.expectedMessageEndpoint = expectedMessageEndpoint;
    }

    public TestEndpoint(String endpointUri, Endpoint expectedMessageEndpoint) {
        super(endpointUri);
        this.expectedMessageEndpoint = expectedMessageEndpoint;
    }

    public void start() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Consuming expected messages from: " + expectedMessageEndpoint);
        }
        final List expectedBodies = new ArrayList();
        EndpointHelper.pollEndpoint(expectedMessageEndpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Object body = getInBody(exchange);
                expectedBodies.add(body);
            }
        }, timeout);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Received: " + expectedBodies.size() + " expected message(s) from: " + expectedMessageEndpoint);
        }
        expectedBodiesReceived(expectedBodies);
    }

    public void stop() throws Exception {
    }

    /**
     * This method allows us to convert or coerce the expected message body into some other type
     */
    protected Object getInBody(Exchange exchange) {
        return exchange.getIn().getBody();
    }
}
