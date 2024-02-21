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

package org.apache.camel.component.huaweicloud.image;

import com.huaweicloud.sdk.image.v2.model.ImageTaggingResponseResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.image.constants.ImageRecognitionProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TagRecognitionWithImageContentAndMockClientTest extends CamelTestSupport {
    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("imageClient")
    ImageClientMock imageClient = new ImageClientMock(null);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:trigger_route")
                        .setProperty(ImageRecognitionProperties.IMAGE_CONTENT,
                                constant(testConfiguration.getProperty("imageContent")))
                        .setProperty(ImageRecognitionProperties.THRESHOLD,
                                constant(testConfiguration.getProperty("tagThreshold")))
                        .to("hwcloud-imagerecognition:tagRecognition?accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey") + "&projectId="
                            + testConfiguration.getProperty("projectId") + "&region="
                            + testConfiguration.getProperty("region") + "&ignoreSslVerification=true"
                            + "&imageClient=#imageClient")
                        .log("perform tag recognition successful")
                        .to("mock:perform_tag_recognition_result");
            }
        };
    }

    @Test
    public void testTagRecognition() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:perform_tag_recognition_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:trigger_route", "");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        ImageTaggingResponseResult response = responseExchange.getIn().getBody(ImageTaggingResponseResult.class);

        assertTrue(response instanceof ImageTaggingResponseResult);

        assertEquals(1, response.getTags().size());
        assertEquals(MockResult.TAG_RECOGNITION_RESULT_TAG, response.getTags().get(0).getTag());
        assertEquals(MockResult.TAG_RECOGNITION_RESULT_TYPE, response.getTags().get(0).getType());
        assertEquals(MockResult.TAG_RECOGNITION_RESULT_CONFIDENCE,
                response.getTags().get(0).getConfidence());
    }

}
