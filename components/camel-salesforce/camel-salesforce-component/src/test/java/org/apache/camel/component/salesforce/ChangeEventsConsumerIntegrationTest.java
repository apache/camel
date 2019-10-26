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
package org.apache.camel.component.salesforce;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * During integration tests setup, Salesforce has been configured to fire change
 * events for Account objects. This test merely uses some API calls to trigger
 * some change events, and then perform assertion on the received events.
 */
@Category(Standalone.class)
public class ChangeEventsConsumerIntegrationTest extends AbstractSalesforceTestBase {

    private static final String ACCOUNT_NAME = "ChangeEventsConsumerIntegrationTest-TestAccount";
    private static final String ACCOUNT_DESCRIPTION = "Account used to check that creation, update and deletion fire change events";

    @EndpointInject(value = "mock:capturedChangeEvents")
    private MockEndpoint capturedChangeEvents;

    @Test
    @SuppressWarnings("unchecked")
    public void accountChangesShouldTriggerChangeEvents() {
        // Trigger a CREATE event for an Account
        final Account account = new Account();
        account.setName(ACCOUNT_NAME);
        final CreateSObjectResult result = template.requestBody("salesforce:createSObject?sObjectName=Account", account, CreateSObjectResult.class);
        Assert.assertNotNull(result.getId());

        // Trigger an UPDATE event for an Account
        account.setDescription(ACCOUNT_DESCRIPTION);
        account.setId(result.getId());
        template.sendBody("salesforce:updateSObject", account);

        // Trigger a DELETE event for an Account
        template.sendBody("salesforce:deleteSObject", account);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Assertions.assertThat(capturedChangeEvents.assertExchangeReceived(2) != null).isTrue();
        });

        final Message createEvent = capturedChangeEvents.getExchanges().get(0).getIn();
        Assert.assertNotNull(createEvent);
        Assert.assertEquals("CREATE", createEvent.getHeader("CamelSalesforceChangeType"));
        final Map<String, Object> createEventBody = createEvent.getBody(Map.class);
        Assert.assertNotNull(createEventBody);
        Assert.assertEquals(ACCOUNT_NAME, createEventBody.get("Name"));
        Assert.assertFalse(createEventBody.containsKey("Description"));

        final Message updateEvent = capturedChangeEvents.getExchanges().get(1).getIn();
        Assert.assertNotNull(updateEvent);
        Assert.assertEquals("UPDATE", updateEvent.getHeader("CamelSalesforceChangeType"));
        final Map<String, Object> updateEventBody = updateEvent.getBody(Map.class);
        Assert.assertNotNull(updateEventBody);
        Assert.assertFalse(updateEventBody.containsKey("Name"));
        Assert.assertEquals(ACCOUNT_DESCRIPTION, updateEventBody.get("Description"));

        final Message deleteEvent = capturedChangeEvents.getExchanges().get(2).getIn();
        Assert.assertNotNull(deleteEvent);
        Assert.assertEquals("DELETE", deleteEvent.getHeader("CamelSalesforceChangeType"));
        final Map<String, Object> deleteEventBody = deleteEvent.getBody(Map.class);
        Assert.assertFalse(deleteEventBody.containsKey("Name"));
        Assert.assertFalse(deleteEventBody.containsKey("Description"));
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("salesforce:data/ChangeEvents?replayId=-1").to(capturedChangeEvents);
            }
        };
    }

    @Override
    protected String salesforceApiVersionToUse() {
        return "45.0";
    }
}
