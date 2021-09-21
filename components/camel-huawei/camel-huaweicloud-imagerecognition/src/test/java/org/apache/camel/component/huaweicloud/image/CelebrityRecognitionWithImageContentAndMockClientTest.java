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

import java.util.*;

import com.huaweicloud.sdk.image.v2.model.CelebrityRecognitionResultBody;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.image.constants.ImageRecognitionProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CelebrityRecognitionWithImageContentAndMockClientTest extends CamelTestSupport {
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
                                constant(testConfiguration.getProperty("celebrityThreshold")))
                        .to("hwcloud-imagerecognition:celebrityRecognition?accessKey="
                            + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey") + "&projectId="
                            + testConfiguration.getProperty("projectId") + "&region="
                            + testConfiguration.getProperty("region") + "&ignoreSslVerification=true"
                            + "&imageClient=#imageClient")
                        .log("perform celebrity recognition successful")
                        .to("mock:perform_celebrity_recognition_result");
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCelebrityRecognition() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:perform_celebrity_recognition_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:trigger_route", "");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        CelebrityRecognitionResultBody result
                = (CelebrityRecognitionResultBody) ((List) responseExchange.getIn().getBody()).get(0);
        assertTrue(result instanceof CelebrityRecognitionResultBody);

        assertEquals(MockResult.CELEBRITY_RECOGNITION_RESULT_LABEL, result.getLabel());
        assertEquals(MockResult.CELEBRITY_RECOGNITION_RESULT_CONFIDENCE, result.getConfidence());
        assertTrue(result.getFaceDetail() instanceof Map);

        Map<String, Integer> faceDetailMap = (Map<String, Integer>) result.getFaceDetail();
        assertEquals(300, faceDetailMap.get("w"));
        assertEquals(500, faceDetailMap.get("h"));
        assertEquals(200, faceDetailMap.get("x"));
        assertEquals(100, faceDetailMap.get("y"));
    }

}
