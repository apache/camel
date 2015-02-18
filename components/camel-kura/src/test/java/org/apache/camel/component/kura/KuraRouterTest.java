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
package org.apache.camel.component.kura;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

public class KuraRouterTest extends Assert {

    TestKuraRouter router = new TestKuraRouter();

    BundleContext bundleContext = mock(BundleContext.class, RETURNS_DEEP_STUBS);

    @Before
    public void before() throws Exception {
        given(bundleContext.getBundle().getVersion().toString()).willReturn("version");

        router.start(bundleContext);
    }

    @After
    public void after() throws Exception {
        router.start(bundleContext);
    }

    @Test
    public void shouldCloseCamelContext() throws Exception {
        // When
        router.stop(bundleContext);

        // Then
        Assert.assertEquals(ServiceStatus.Stopped, router.camelContext.getStatus());
    }

    @Test
    public void shouldStartCamelContext() throws Exception {
        // Given
        String message = "foo";
        MockEndpoint mockEndpoint = router.camelContext.getEndpoint("mock:test", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived(message);

        // When
        router.producerTemplate.sendBody("direct:start", message);

        // Then
        mockEndpoint.assertIsSatisfied();
    }

}

class TestKuraRouter extends KuraRouter {

    @Override
    public void configure() throws Exception {
        from("direct:start").to("mock:test");
    }

    @Override
    protected CamelContext createCamelContext() {
        return new DefaultCamelContext();
    }

}