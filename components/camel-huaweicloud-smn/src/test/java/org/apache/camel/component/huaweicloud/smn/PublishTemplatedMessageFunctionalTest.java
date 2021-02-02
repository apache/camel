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
import org.apache.camel.component.huaweicloud.smn.constants.SmnProperties;
import org.apache.camel.component.huaweicloud.smn.models.ServiceKeys;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublishTemplatedMessageFunctionalTest extends CamelTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublishTemplatedMessageTest.class.getName());

    private static final String AUTH_KEY = "replace_this_with_auth_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String TEMPLATE_NAME = "replace_this_with_template_name";
    private static final String NOTIFICATION_SUBJECT = "sample notification subjectline";
    private static final String TOPIC_NAME = "replace_this_with_topic_name";
    private static final String PROJECT_ID = "replace_this_with_project_id";
    private static final String REGION = "replace_this_with_region";

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys
            = new ServiceKeys(AUTH_KEY, SECRET_KEY);

    protected RouteBuilder createRouteBuilder() throws Exception {
        // populating tag values. user has to adjust the map entries according to the structure of their respective templates
        Map<String, String> tags = new HashMap<>();

        // create a map of your placeholder variables
        /*Example :
        tags.put("name", "reji");
        tags.put("phone", "1234567890");
        */
        return new RouteBuilder() {
            public void configure() {
                from("direct:publish_templated_message")
                        .setProperty(SmnProperties.NOTIFICATION_SUBJECT, constant(NOTIFICATION_SUBJECT))
                        .setProperty(SmnProperties.NOTIFICATION_TOPIC_NAME, constant(TOPIC_NAME))
                        .setProperty(SmnProperties.NOTIFICATION_TTL, constant(60))
                        .setProperty(SmnProperties.TEMPLATE_TAGS, constant(tags))
                        .setProperty(SmnProperties.TEMPLATE_NAME, constant(TEMPLATE_NAME))
                        .to("hwcloud-smn:publishMessageService?serviceKeys=#serviceKeys&operation=publishAsTemplatedMessage"
                            + "&projectId=" + PROJECT_ID
                            + "&region=" + REGION
                            + "&ignoreSslVerification=true")
                        .log("templated notification sent")
                        .to("mock:publish_templated_message_result");
            }
        };
    }

    /**
     * following test cases should be manually enabled to perform test against the actual huaweicloud simple
     * notification server with real user credentials. To perform this test, manually comment out the @Disabled
     * annotation and enter relevant service parameters in the placeholders above (static variables of this test class)
     *
     * @throws Exception
     */
    @Test
    @Disabled("Manually enable this once you configure service parameters in placeholders above")
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
