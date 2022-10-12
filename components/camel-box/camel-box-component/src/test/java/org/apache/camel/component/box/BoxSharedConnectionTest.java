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
package org.apache.camel.component.box;

import com.box.sdk.BoxAPIConnection;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxConnectionHelper;
import org.apache.camel.component.box.internal.BoxFilesManagerApiMethod;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class BoxSharedConnectionTest {

    private static final String PATH_PREFIX
            = BoxApiCollection.getCollection().getApiName(BoxFilesManagerApiMethod.class).getName();

    @Test
    void testEndpointUsesSharedConnection() throws Exception {
        final String boxUri = "box:" + PATH_PREFIX + "/getFileInfo";

        BoxConfiguration configuration = createBoxConfiguration();

        BoxComponent component = new BoxComponent();
        component.setConfiguration(configuration);

        try (CamelContext camelContext = createCamelContext(boxUri, component)) {

            BoxAPIConnection connection = Mockito.mock(BoxAPIConnection.class);

            try (MockedStatic<BoxConnectionHelper> helper = Mockito.mockStatic(BoxConnectionHelper.class)) {
                helper.when(() -> BoxConnectionHelper.createConnection(configuration)).thenReturn(connection);

                camelContext.start();
                BoxEndpoint endpoint = camelContext.getEndpoint(boxUri, BoxEndpoint.class);

                helper.verify(() -> BoxConnectionHelper.createConnection(configuration), Mockito.times(1));

                Assertions.assertSame(component.getBoxConnection(), endpoint.getBoxConnection());
            }
        }
    }

    @Test
    void testEndpointOverridesSharedConnection() throws Exception {
        String boxUri = "box:" + PATH_PREFIX + "/getFileInfo?userPassword=0th3rP4ssw0rd";

        BoxComponent component = new BoxComponent();
        component.setConfiguration(createBoxConfiguration());

        try (CamelContext camelContext = createCamelContext(boxUri, component)) {
            BoxAPIConnection componentConnection = Mockito.mock(BoxAPIConnection.class);
            BoxAPIConnection endpointConnection = Mockito.mock(BoxAPIConnection.class);

            try (MockedStatic<BoxConnectionHelper> helper = Mockito.mockStatic(BoxConnectionHelper.class)) {
                helper.when(() -> BoxConnectionHelper.createConnection(Mockito.isA(BoxConfiguration.class)))
                        .thenReturn(componentConnection, endpointConnection);

                camelContext.start();
                BoxEndpoint endpoint = camelContext.getEndpoint(boxUri, BoxEndpoint.class);

                helper.verify(() -> BoxConnectionHelper.createConnection(Mockito.any()), Mockito.times(2));

                Assertions.assertSame(componentConnection, component.getBoxConnection());
                Assertions.assertSame(endpointConnection, endpoint.getBoxConnection());
            }
        }
    }

    private static CamelContext createCamelContext(String boxUri, BoxComponent component) throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addComponent("box", component);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(boxUri);
            }
        });
        return camelContext;
    }

    private static BoxConfiguration createBoxConfiguration() {
        BoxConfiguration configuration = new BoxConfiguration();
        configuration.setUserName("camel@apache.org");
        configuration.setUserPassword("p4ssw0rd");
        configuration.setClientId("camel-client-id");
        configuration.setClientSecret("camel-client-secret");
        configuration.setAuthenticationType("STANDARD_AUTHENTICATION");
        return configuration;
    }
}
