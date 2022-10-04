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
package org.apache.camel.component.google.mail;

import com.google.api.services.gmail.model.ListMessagesResponse;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.mail.internal.GmailUsersMessagesApiMethod;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link com.google.api.services.gmail.Gmail$Users$Messages} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.google.mail.AbstractGoogleMailTestSupport#hasCredentials",
           disabledReason = "Google Mail credentials were not provided")
public class GmailUsersMessagesConsumerIT extends AbstractGoogleMailTestSupport {

    // userid of the currently authenticated user
    public static final String CURRENT_USERID = "me";
    private static final Logger LOG = LoggerFactory.getLogger(GmailUsersMessagesConsumerIT.class);
    private static final String PATH_PREFIX
            = GoogleMailApiCollection.getCollection().getApiName(GmailUsersMessagesApiMethod.class).getName();

    @Test
    public void testConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        assertTrue(mock.getExchanges().get(0).getIn().getBody() instanceof ListMessagesResponse);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("google-mail://" + PATH_PREFIX + "/list?userId=me").to("mock:result");

            }
        };
    }
}
