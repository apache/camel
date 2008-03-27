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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.CamelContextHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * @version $Revision$
 */
@ContextConfiguration
public class TestEndpointTest extends AbstractJUnit38SpringContextTests {
    private static final transient Log LOG = LogFactory.getLog(TestEndpointTest.class);

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject(uri = "test:file://src/test/data?noop=true")
    protected TestEndpoint endpoint;

    public void testMocksAreValid() throws Exception {
        assertNotNull(camelContext);
        assertNotNull(endpoint);

        MockEndpoint.assertIsSatisfied(camelContext);

        // lets show the endpoints in the test
        List<MockEndpoint> list = CamelContextHelper.getSingletonEndpoints(camelContext, MockEndpoint.class);
        LOG.debug("Found endpoints: " + list);

        // lets dump the messages sent to our test endpoint
        List<Exchange> exchanges = endpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            LOG.debug("Received: " + exchange);
        }
    }
}
