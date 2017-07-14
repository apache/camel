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
package org.apache.camel.component.google.mail;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.mail.internal.GmailUsersMessagesApiMethod;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link GoogleMailConfiguration}.
 */
public class GmailConfigurationTest extends AbstractGoogleMailTestSupport {

    // userid of the currently authenticated user
    public static final String CURRENT_USERID = "me";
    private static final Logger LOG = LoggerFactory.getLogger(GmailConfigurationTest.class);
    private static final String PATH_PREFIX = GoogleMailApiCollection.getCollection().getApiName(GmailUsersMessagesApiMethod.class).getName();
    private static final String TEST_URI = "google-mail://" + PATH_PREFIX + "/send?clientId=a&clientSecret=b&applicationName=c&accessToken=d&refreshToken=e";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = new DefaultCamelContext(createRegistry());

        // add GoogleMailComponent to Camel context but don't set up configuration
        final GoogleMailComponent component = new GoogleMailComponent(context);
        context.addComponent("google-mail", component);

        return context;
    }
    
    @Test
    public void testConfiguration() throws Exception {
        GoogleMailEndpoint endpoint = getMandatoryEndpoint(TEST_URI, GoogleMailEndpoint.class);
        GoogleMailConfiguration configuration = endpoint.getConfiguration();
        assertNotNull(configuration);
        assertEquals("a", configuration.getClientId());
        assertEquals("b", configuration.getClientSecret());
        assertEquals("c", configuration.getApplicationName());
        assertEquals("d", configuration.getAccessToken());
        assertEquals("e", configuration.getRefreshToken());        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for send
                from("direct://SEND").to(TEST_URI);
            }
        };
    }
}
