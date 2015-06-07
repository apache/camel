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

import java.util.HashMap;
import java.util.Map;

import com.google.api.services.gmail.model.Label;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.mail.internal.GmailUsersLabelsApiMethod;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.gmail.Gmail$Users$Labels} APIs.
 */
public class GmailUsersLabelsIntegrationTest extends AbstractGoogleMailTestSupport {

    private static final String CAMEL_TEST_LABEL = "CamelTestLabel";
    private static final Logger LOG = LoggerFactory.getLogger(GmailUsersLabelsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleMailApiCollection.getCollection().getApiName(GmailUsersLabelsApiMethod.class).getName();

    @Test
    public void testLabels() throws Exception {
        // using String message body for single parameter "userId"
        com.google.api.services.gmail.model.ListLabelsResponse labels = requestBody("direct://LIST", CURRENT_USERID);

        String labelId = null;
        if (getTestLabel(labels) == null) {
            Map<String, Object> headers = new HashMap<String, Object>();
            // parameter type is String
            headers.put("CamelGoogleMail.userId", CURRENT_USERID);
            Label label = new Label().setName(CAMEL_TEST_LABEL).setMessageListVisibility("show").setLabelListVisibility("labelShow");
            // parameter type is com.google.api.services.gmail.model.Label
            headers.put("CamelGoogleMail.content", label);

            com.google.api.services.gmail.model.Label result = requestBodyAndHeaders("direct://CREATE", null, headers);

            assertNotNull("create result", result);
            labelId = result.getId();
        } else {
            labelId = getTestLabel(labels).getId();
        }

        // using String message body for single parameter "userId"
        labels = requestBody("direct://LIST", CURRENT_USERID);
        assertTrue(getTestLabel(labels) != null);

        Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleMail.userId", CURRENT_USERID);
        // parameter type is String
        headers.put("CamelGoogleMail.id", labelId);

        requestBodyAndHeaders("direct://DELETE", null, headers);

        // using String message body for single parameter "userId"
        labels = requestBody("direct://LIST", CURRENT_USERID);
        assertTrue(getTestLabel(labels) == null);
    }

    private Label getTestLabel(com.google.api.services.gmail.model.ListLabelsResponse labels) {
        for (Label label : labels.getLabels()) {
            if (CAMEL_TEST_LABEL.equals(label.getName())) {
                return label;
            }
        }
        return null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for create
                from("direct://CREATE").to("google-mail://" + PATH_PREFIX + "/create");

                // test route for delete
                from("direct://DELETE").to("google-mail://" + PATH_PREFIX + "/delete");

                // test route for get
                from("direct://GET").to("google-mail://" + PATH_PREFIX + "/get");

                // test route for list
                from("direct://LIST").to("google-mail://" + PATH_PREFIX + "/list?inBody=userId");

                // test route for patch
                from("direct://PATCH").to("google-mail://" + PATH_PREFIX + "/patch");

                // test route for update
                from("direct://UPDATE").to("google-mail://" + PATH_PREFIX + "/update");

            }
        };
    }
}
