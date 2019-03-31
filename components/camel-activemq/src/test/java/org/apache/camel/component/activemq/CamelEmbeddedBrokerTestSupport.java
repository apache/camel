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
package org.apache.camel.component.activemq;

import org.apache.activemq.EmbeddedBrokerTestSupport;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * A helper class for test cases which use an embedded broker and use Camel to
 * do the routing
 */
public abstract class CamelEmbeddedBrokerTestSupport extends EmbeddedBrokerTestSupport {
    protected CamelContext camelContext;
    protected ProducerTemplate template;

    @Override
    protected void setUp() throws Exception {
        bindAddress = "tcp://localhost:61616";
        super.setUp();
        camelContext = createCamelContext();
        addCamelRoutes(camelContext);
        assertValidContext(camelContext);
        camelContext.start();
        template = camelContext.createProducerTemplate();
        template.start();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (template != null) {
            template.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    protected void addCamelRoutes(CamelContext camelContext) throws Exception {
    }

    /**
     * Resolves a mandatory endpoint for the given URI or an exception is thrown
     *
     * @param uri the Camel <a href="">URI</a> to use to create or resolve an
     *            endpoint
     * @return the endpoint
     */
    protected Endpoint resolveMandatoryEndpoint(String uri) {
        return resolveMandatoryEndpoint(camelContext, uri);
    }

    /**
     * Resolves a mandatory endpoint for the given URI and expected type or an
     * exception is thrown
     *
     * @param uri the Camel <a href="">URI</a> to use to create or resolve an
     *            endpoint
     * @return the endpoint
     */
    protected <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return resolveMandatoryEndpoint(camelContext, uri, endpointType);
    }

    /**
     * Resolves an endpoint and asserts that it is found
     */
    protected Endpoint resolveMandatoryEndpoint(CamelContext context, String uri) {
        Endpoint endpoint = context.getEndpoint(uri);

        assertNotNull("No endpoint found for URI: " + uri, endpoint);

        return endpoint;
    }

    /**
     * Resolves an endpoint and asserts that it is found
     */
    protected <T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String uri, Class<T> endpointType) {
        T endpoint = context.getEndpoint(uri, endpointType);

        assertNotNull("No endpoint found for URI: " + uri, endpoint);

        return endpoint;
    }

    /**
     * Resolves the mandatory Mock endpoint using a URI of the form
     * <code>mock:someName</code>
     *
     * @param uri the URI which typically starts with "mock:" and has some name
     * @return the mandatory mock endpoint or an exception is thrown if it could
     *         not be resolved
     */
    protected MockEndpoint getMockEndpoint(String uri) {
        return resolveMandatoryEndpoint(uri, MockEndpoint.class);
    }

    /**
     * Asserts that all the expectations of the Mock endpoints are valid
     */
    protected void assertMockEndpointsSatisifed() throws InterruptedException {
        MockEndpoint.assertIsSatisfied(camelContext);
    }

    protected void assertValidContext(CamelContext context) {
        assertNotNull("No context found!", context);
    }
}
