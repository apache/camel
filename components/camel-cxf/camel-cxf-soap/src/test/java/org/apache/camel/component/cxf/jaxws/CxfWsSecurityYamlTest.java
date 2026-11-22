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
package org.apache.camel.component.cxf.jaxws;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.binding.soap.SoapFault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A YAML route that sets up a WS-Security UsernameToken client and server with endpoint options only.
 */
class CxfWsSecurityYamlTest extends CamelTestSupport {

    private static final String REQUEST
            = "<getOrder xmlns=\"http://camel.apache.org/wssec/orders\"><orderId>1</orderId></getOrder>";

    private static AvailablePortFinder.Port port;

    @BeforeAll
    static void allocatePort() {
        port = AvailablePortFinder.find();
    }

    @AfterAll
    static void releasePort() {
        if (port != null) {
            port.release();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().addInitialProperty("wssec.port", Integer.toString(port.getPort()));
        PluginHelper.getRoutesLoader(context)
                .loadRoutes(ResourceHelper.resolveMandatoryResource(context, "classpath:wssec/wssec.camel.yaml"));
        return context;
    }

    @Test
    void usernameTokenFromYaml() {
        Exchange exchange = template.request("direct:yaml", e -> e.getIn().setBody(REQUEST));
        assertNull(exchange.getException());
        String reply = exchange.getMessage().getBody(String.class);
        assertTrue(reply.contains("<user>ok</user>"), reply);
    }

    @Test
    void wrongPasswordFromYaml() {
        Exchange exchange = template.request("direct:yamlBad", e -> e.getIn().setBody(REQUEST));
        assertInstanceOf(SoapFault.class, exchange.getException());
    }
}
