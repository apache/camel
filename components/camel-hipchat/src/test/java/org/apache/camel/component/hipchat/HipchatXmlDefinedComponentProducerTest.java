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
package org.apache.camel.component.hipchat;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.Model;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.hamcrest.core.Is.is;

public class HipchatXmlDefinedComponentProducerTest extends CamelTestSupport {

    @EndpointInject("hipchat:https:foobar.com:443?authToken=abc123")
    protected Endpoint endpoint;

    @Test
    public void shouldConfigureEndpointCorrectlyViaXml() throws Exception {
        assertIsInstanceOf(HipchatEndpoint.class, endpoint);
        HipchatEndpoint hipchatEndpoint = (HipchatEndpoint) endpoint;
        HipchatConfiguration configuration = hipchatEndpoint.getConfiguration();
        assertThat(configuration.getAuthToken(), is("abc123"));
        assertThat(configuration.getHost(), is("foobar.com"));
        assertThat(configuration.getProtocol(), is("https"));
        assertThat(configuration.getPort(), is(443));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        HipchatComponent component = new HipchatTestComponent(context);
        component.init();
        context.addComponent("hipchat", component);

        // This test is all about ensuring the endpoint is configured correctly when using the XML DSL so this
        try (InputStream routes = getClass().getResourceAsStream("HipchatXmlDefinedComponentProducerTest-route.xml")) {
            ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
            RoutesDefinition routesDefinition = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(ecc, routes);
            context.getExtension(Model.class).addRouteDefinition(routesDefinition.getRoutes().get(0));
        }

        return context;
    }
}
