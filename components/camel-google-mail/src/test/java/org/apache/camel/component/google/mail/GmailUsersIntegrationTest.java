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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.mail.internal.GmailUsersApiMethod;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.gmail.Gmail$Users} APIs.
 */
public class GmailUsersIntegrationTest extends AbstractGoogleMailTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GmailUsersIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleMailApiCollection.getCollection().getApiName(GmailUsersApiMethod.class).getName();

    @Test
    public void testGetProfile() throws Exception {
        // using String message body for single parameter "userId"
        final com.google.api.services.gmail.model.Profile result = requestBody("direct://GETPROFILE", CURRENT_USERID);

        assertNotNull("getProfile result", result);
        assertNotNull("Should be email address associated with current account", result.getEmailAddress());
        LOG.debug("getProfile: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for getProfile
                from("direct://GETPROFILE").to("google-mail://" + PATH_PREFIX + "/getProfile?inBody=userId");

            }
        };
    }
}
