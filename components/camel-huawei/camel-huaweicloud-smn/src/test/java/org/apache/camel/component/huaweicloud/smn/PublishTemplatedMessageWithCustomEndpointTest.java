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
package org.apache.camel.component.huaweicloud.smn;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.smn.constants.SmnProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublishTemplatedMessageWithCustomEndpointTest extends CamelTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublishTemplatedMessageWithCustomEndpointTest.class.getName());

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys
            = new ServiceKeys(testConfiguration.getProperty("accessKey"), testConfiguration.getProperty("secretKey"));

    @BindToRegistry("smnClient")
    SmnClientMock smnClientMock = new SmnClientMock(null); // creating mock smn client to stub method behavior for unit testing

    protected RouteBuilder createRouteBuilder() {
        // populating tag values. user has to adjust the map entries according to the structure of their respective templates
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "reji");
        tags.put("phone", "1234567890");

        return new RouteBuilder() {
            public void configure() {
                from("direct:publish_templated_message")
                        .setProperty(SmnProperties.NOTIFICATION_SUBJECT, constant("This is my subjectline"))
                        .setProperty(SmnProperties.NOTIFICATION_TOPIC_NAME, constant(testConfiguration.getProperty("topic")))
                        .setProperty(SmnProperties.NOTIFICATION_TTL, constant(60))
                        .setProperty(SmnProperties.TEMPLATE_TAGS, constant(tags))
                        .setProperty(SmnProperties.TEMPLATE_NAME, constant("hello-template"))
                        .to("hwcloud-smn:publishMessageService?serviceKeys=#serviceKeys&operation=publishAsTemplatedMessage"
                            + "&projectId=" + testConfiguration.getProperty("projectId") + "&region="
                            + testConfiguration.getProperty("region") + "&ignoreSslVerification=true"
                            + "&endpoint=" + testConfiguration.getProperty("endpoint")
                            + "&smnClient=#smnClient")
                        .log("templated notification sent")
                        .to("mock:publish_templated_message_result");
            }
        };
    }

    @Test
    public void testTemplatedNotificationSend() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:publish_templated_message_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:publish_templated_message", null);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertNotNull(responseExchange.getProperty(SmnProperties.SERVICE_MESSAGE_ID));
        assertNotNull(responseExchange.getProperty(SmnProperties.SERVICE_REQUEST_ID));
        assertTrue(responseExchange.getProperty(SmnProperties.SERVICE_MESSAGE_ID).toString().length() > 0);
        assertTrue(responseExchange.getProperty(SmnProperties.SERVICE_REQUEST_ID).toString().length() > 0);
    }

}
