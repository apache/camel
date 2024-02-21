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
package org.apache.camel.component.huaweicloud.frs.mock;

import com.huaweicloud.sdk.frs.v2.model.CompareFaceByBase64Response;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.frs.TestConfiguration;
import org.apache.camel.component.huaweicloud.frs.constants.FaceRecognitionProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FaceVerificationWithImageBae64AndMockClientTest extends CamelTestSupport {
    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("frsClient")
    FrsClientMock frsClient = new FrsClientMock(null);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:trigger_route_01")
                        .setProperty(FaceRecognitionProperties.FACE_IMAGE_BASE64,
                                constant(testConfiguration.getProperty("imageBase64")))
                        .setProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_BASE64,
                                constant(testConfiguration.getProperty("anotherImageBase64")))
                        .to("hwcloud-frs:faceVerification?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true"
                            + "&frsClient=#frsClient")
                        .log("perform faceVerification successfully")
                        .to("mock:perform_face_verification_result_01");

                from("direct:trigger_route_02")
                        .setProperty(FaceRecognitionProperties.FACE_IMAGE_BASE64,
                                constant(testConfiguration.getProperty("imageBase64")))
                        .to("hwcloud-frs:faceVerification?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&anotherImageBase64=" + constant(testConfiguration.getProperty("anotherImageBase64"))
                            + "&ignoreSslVerification=true"
                            + "&frsClient=#frsClient")
                        .log("perform faceVerification successfully")
                        .to("mock:perform_face_verification_result_02");
            }
        };
    }

    /**
     * use imageBase64 to perform faceVerification
     *
     * @throws Exception
     */
    @Test
    public void testFaceVerification01() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:perform_face_verification_result_01");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:trigger_route_01", "");
        Exchange responseExchange = mock.getExchanges().get(0);
        mock.assertIsSatisfied();

        assertTrue(responseExchange.getIn().getBody() instanceof CompareFaceByBase64Response);
        CompareFaceByBase64Response response = (CompareFaceByBase64Response) responseExchange.getIn().getBody();
        assertEquals(response.getImage1Face(), MockResult.getCompareFaceResult());
        assertEquals(response.getImage2Face(), MockResult.getCompareFaceResult());
        assertEquals(1.0, response.getSimilarity());
    }

    /**
     * use imageBase64 to perform faceVerification
     *
     * @throws Exception
     */
    @Test
    public void testFaceVerification02() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:perform_face_verification_result_02");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:trigger_route_02", "");
        Exchange responseExchange = mock.getExchanges().get(0);
        mock.assertIsSatisfied();

        assertTrue(responseExchange.getIn().getBody() instanceof CompareFaceByBase64Response);
        CompareFaceByBase64Response response = (CompareFaceByBase64Response) responseExchange.getIn().getBody();
        assertEquals(MockResult.getCompareFaceResult(), response.getImage1Face());
        assertEquals(MockResult.getCompareFaceResult(), response.getImage2Face());
        assertEquals(1.0, response.getSimilarity());
    }

}
