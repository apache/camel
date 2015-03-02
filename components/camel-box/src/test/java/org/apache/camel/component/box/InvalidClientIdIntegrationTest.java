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
package org.apache.camel.component.box;

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.IBoxSharedItemsManagerApiMethod;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test Box component configuration.
 */
public class InvalidClientIdIntegrationTest extends AbstractBoxTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(InvalidClientIdIntegrationTest.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection().getApiName(IBoxSharedItemsManagerApiMethod.class).getName();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();

        // set client_id to an invalid value
        final BoxConfiguration configuration = camelContext.getComponent("box", BoxComponent.class).getConfiguration();
        configuration.setClientId("bad_client_id");

        // also remove auth secure storage to avoid loading a stored token
        configuration.setAuthSecureStorage(null);

        return camelContext;
    }

    @Override
    protected void startCamelContext() throws Exception {
        // should throw an exception on start
        try {
            super.startCamelContext();
            fail("Invalid client id MUST cause an IllegalArgumentException on startup");
        } catch (FailedToCreateRouteException e) {
            Throwable t = e;
            while (t.getCause() != null && t.getCause() != e) {
                t = t.getCause();
            }
            assertNotNull("root cause exception", t);
            assertEquals("illegal argument exception", IllegalArgumentException.class, t.getClass());
            LOG.debug("Caught expected exception {}", t.getMessage());
        }
    }

    @Test
    public void testInvalidClientId() throws Exception {
        // do nothing
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // dummy route to force box component startup
                from("direct://GETSHAREDITEM")
                    .to("box://" + PATH_PREFIX + "/getSharedItem?inBody=defaultRequest");
            }
        };
    }
}
