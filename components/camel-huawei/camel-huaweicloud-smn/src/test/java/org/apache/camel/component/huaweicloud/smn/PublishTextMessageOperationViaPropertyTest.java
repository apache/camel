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

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.smn.constants.SmnOperations;
import org.apache.camel.component.huaweicloud.smn.constants.SmnProperties;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the operation is supplied only through the {@code CamelHwCloudSmnOperation} exchange property (with no
 * {@code operation} endpoint parameter), an empty text-message body must fail with the clear
 * {@code IllegalArgumentException} - not a {@code NullPointerException} from the guard testing the (null) endpoint
 * operation, which was the behaviour before the operation was resolved ahead of the body check.
 */
public class PublishTextMessageOperationViaPropertyTest extends CamelTestSupport {

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("smnClient")
    SmnClientMock smnClientMock = new SmnClientMock(null);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:publish_without_operation_param")
                        .setProperty(SmnProperties.NOTIFICATION_SUBJECT, constant("Dummy Subject Line"))
                        .setProperty(SmnProperties.NOTIFICATION_TOPIC_NAME, constant(testConfiguration.getProperty("topic")))
                        .setProperty(SmnProperties.NOTIFICATION_TTL, constant(60))
                        // operation supplied only via the exchange property; the endpoint has no operation= parameter
                        .setProperty(SmnProperties.SMN_OPERATION, constant(SmnOperations.PUBLISH_AS_TEXT_MESSAGE))
                        .to("hwcloud-smn:publishMessageService?accessKey="
                            + testConfiguration.getProperty("accessKey") + "&secretKey="
                            + testConfiguration.getProperty("secretKey") + "&projectId="
                            + testConfiguration.getProperty("projectId") + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true"
                            + "&smnClient=#smnClient")
                        .to("mock:result");

                from("direct:templated_endpoint_text_property")
                        .setProperty(SmnProperties.NOTIFICATION_SUBJECT, constant("Dummy Subject Line"))
                        .setProperty(SmnProperties.NOTIFICATION_TOPIC_NAME, constant(testConfiguration.getProperty("topic")))
                        .setProperty(SmnProperties.NOTIFICATION_TTL, constant(60))
                        // endpoint operation is templated, but the property overrides it to text
                        .setProperty(SmnProperties.SMN_OPERATION, constant(SmnOperations.PUBLISH_AS_TEXT_MESSAGE))
                        .to("hwcloud-smn:publishMessageService?operation=" + SmnOperations.PUBLISH_AS_TEMPLATED_MESSAGE
                            + "&accessKey=" + testConfiguration.getProperty("accessKey") + "&secretKey="
                            + testConfiguration.getProperty("secretKey") + "&projectId="
                            + testConfiguration.getProperty("projectId") + "&region="
                            + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true"
                            + "&smnClient=#smnClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void emptyBodyReportsAClearErrorNotAnNpe() {
        Exchange result = template.request("direct:publish_without_operation_param", e -> e.getIn().setBody(null));

        Throwable cause = result.getException();
        assertNotNull(cause, "an empty text-message body was expected to fail");
        boolean clearError = false;
        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null
                    && cause.getMessage().contains("exchange body cannot be null / empty")) {
                clearError = true;
                break;
            }
            cause = cause.getCause();
        }
        assertTrue(clearError,
                "expected a clear 'exchange body cannot be null / empty' IllegalArgumentException, got: "
                               + result.getException());
    }

    @Test
    public void propertyOverridingTemplatedEndpointToTextRejectsEmptyBody() {
        // endpoint operation is publishAsTemplatedMessage but the property overrides to publishAsTextMessage;
        // the guard must test the RESOLVED operation, otherwise an empty text message is silently published
        Exchange result = template.request("direct:templated_endpoint_text_property", e -> e.getIn().setBody(null));

        Throwable cause = result.getException();
        assertNotNull(cause, "an empty text-message body was expected to fail");
        boolean clearError = false;
        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null
                    && cause.getMessage().contains("exchange body cannot be null / empty")) {
                clearError = true;
                break;
            }
            cause = cause.getCause();
        }
        assertTrue(clearError, "expected 'exchange body cannot be null / empty', got: " + result.getException());
    }
}
