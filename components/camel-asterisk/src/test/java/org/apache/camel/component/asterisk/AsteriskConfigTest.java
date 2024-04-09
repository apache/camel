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
package org.apache.camel.component.asterisk;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsteriskConfigTest implements ConfigurableRoute, CamelTestSupportHelper, ConfigurableContext {

    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private String hostname = "192.168.0.254";
    private String username = "username";
    private String password = "password";
    private String action = "QUEUE_STATUS";

    protected CamelContext context;

    @BeforeEach
    void setupContext() {
        context = camelContextExtension.getContext();
    }

    @Test
    void asteriskEndpointData() {
        Endpoint endpoint = context.getEndpoint("asterisk://myVoIP?hostname=" + hostname + "&username=" + username
                                                + "&password=" + password + "&action=" + action);
        assertTrue(endpoint instanceof AsteriskEndpoint, "Endpoint not an AsteriskEndpoint: " + endpoint);
        AsteriskEndpoint asteriskEndpoint = (AsteriskEndpoint) endpoint;

        assertEquals(hostname, asteriskEndpoint.getHostname());
        assertEquals(username, asteriskEndpoint.getUsername());
        assertEquals(password, asteriskEndpoint.getPassword());
        assertEquals(action, asteriskEndpoint.getAction().name());
    }

    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {

    }

    @Override
    public void configureContext(CamelContext context) throws Exception {

    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }
}
