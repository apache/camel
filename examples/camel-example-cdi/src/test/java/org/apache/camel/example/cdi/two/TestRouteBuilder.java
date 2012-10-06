/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.cdi.two;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Mock;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.cdi.MyRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ApplicationScoped
@ContextName
public class TestRouteBuilder extends RouteBuilder {
    private static final transient Logger LOG = LoggerFactory.getLogger(TestRouteBuilder.class);

    @Inject
    MyRoutes config;

    @Inject
    @Mock
    MockEndpoint result;

    boolean routeConfigured;

    @Override
    public void configure() throws Exception {
        routeConfigured = true;
        Endpoint resultEndpoint = config.getResultEndpoint();
        LOG.info("consuming from output: " + resultEndpoint + " to : " + result);

        from(resultEndpoint).to(result);
    }

    public void assertIsSatisfied() {
        assertTrue("TestRouteBuilder has not been configured!" + routeConfigured, routeConfigured);
        assertNotNull("MockEndpoint result not injected!", result);

        result.expectedMessageCount(2);
    }
}
