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
package org.apache.camel.component.salesforce;

import java.time.ZonedDateTime;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.apache.camel.component.salesforce.internal.dto.QueryRecordsPushTopic;
import org.junit.Test;

public class StreamingApiIntegrationTest extends AbstractSalesforceTestBase {

    @Test
    public void testSubscribeAndReceive() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedMessageCount(1);
        // assert expected static headers
        mock.expectedHeaderReceived("CamelSalesforceTopicName", "CamelTestTopic");
        mock.expectedHeaderReceived("CamelSalesforceChannel", "/topic/CamelTestTopic");

        MockEndpoint rawPayloadMock = getMockEndpoint("mock:RawPayloadCamelTestTopic");
        rawPayloadMock.expectedMessageCount(1);
        // assert expected static headers
        rawPayloadMock.expectedHeaderReceived("CamelSalesforceTopicName", "CamelTestTopic");
        rawPayloadMock.expectedHeaderReceived("CamelSalesforceChannel", "/topic/CamelTestTopic");

        Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("TestNotification");
        merchandise.setDescription__c("Merchandise for testing Streaming API updated on " + ZonedDateTime.now().toString());
        merchandise.setPrice__c(9.99);
        merchandise.setTotal_Inventory__c(1000.0);
        CreateSObjectResult result = template().requestBody(
            "direct:upsertSObject", merchandise, CreateSObjectResult.class);
        assertTrue("Merchandise test record not created",  result == null || result.getSuccess());

        try {
            // wait for Salesforce notification
            mock.assertIsSatisfied();
            final Message in = mock.getExchanges().get(0).getIn();
            merchandise = in.getMandatoryBody(Merchandise__c.class);

            assertNotNull("Missing event body", merchandise);
            log.info("Merchandise notification: {}", merchandise.toString());
            assertNotNull("Missing field Id", merchandise.getId());
            assertNotNull("Missing field Name", merchandise.getName());

            // validate dynamic message headers
            assertNotNull("Missing header CamelSalesforceClientId", in.getHeader("CamelSalesforceClientId"));
            assertNotNull("Missing header CamelSalesforceEventType", in.getHeader("CamelSalesforceEventType"));
            assertNotNull("Missing header CamelSalesforceCreatedDate", in.getHeader("CamelSalesforceCreatedDate"));

            // validate raw payload message
            rawPayloadMock.assertIsSatisfied();
            final Message inRaw = rawPayloadMock.getExchanges().get(0).getIn();
            assertTrue("Expected String message body for Raw Payload", inRaw.getBody() instanceof String);

        } finally {
            // remove the test record
            assertNull(template().requestBody("direct:deleteSObjectWithId", merchandise));

            // remove the test topic
            // find it using SOQL first
            QueryRecordsPushTopic records = template().requestBody("direct:query", null,
                QueryRecordsPushTopic.class);
            assertEquals("Test topic not found", 1, records.getTotalSize());
            assertNull(template().requestBody("direct:deleteSObject",
                records.getRecords().get(0)));

        }
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                // test topic subscription
                //from("salesforce:CamelTestTopic?notifyForFields=ALL&notifyForOperations=ALL&"
                from("salesforce:CamelTestTopic?notifyForFields=ALL&"
                    + "notifyForOperationCreate=true&notifyForOperationDelete=true&notifyForOperationUpdate=true&"
                    + "sObjectName=Merchandise__c&"
                    + "updateTopic=true&sObjectQuery=SELECT Id, Name FROM Merchandise__c").
                    to("mock:CamelTestTopic");

                from("salesforce:CamelTestTopic?rawPayload=true&notifyForFields=ALL&"
                    + "notifyForOperationCreate=true&notifyForOperationDelete=true&notifyForOperationUpdate=true&"
                    + "updateTopic=true&sObjectQuery=SELECT Id, Name FROM Merchandise__c").
                    to("mock:RawPayloadCamelTestTopic");

                // route for creating test record
                from("direct:upsertSObject").
                    to("salesforce:upsertSObject?SObjectIdName=Name");

                // route for finding test topic
                from("direct:query").
                    to("salesforce:query?sObjectQuery=SELECT Id FROM PushTopic WHERE Name = 'CamelTestTopic'&"
                        + "sObjectClass=" + QueryRecordsPushTopic.class.getName());

                // route for removing test record
                from("direct:deleteSObjectWithId").
                    to("salesforce:deleteSObjectWithId?sObjectIdName=Name");

                // route for removing topic
                from("direct:deleteSObject").
                    to("salesforce:deleteSObject");

            }
        };
    }
}
