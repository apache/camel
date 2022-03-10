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
package org.apache.camel.component.huaweicloud.obs;

import com.obs.services.ObsClient;
import com.obs.services.model.BucketMetadataInfoRequest;
import com.obs.services.model.BucketMetadataInfoResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BucketMetadataTest extends CamelTestSupport {

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("obsClient")
    ObsClient mockClient = Mockito.mock(ObsClient.class);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bucket_metadata")
                        .setProperty("CamelHwCloudObsBucketName", constant(testConfiguration.getProperty("bucketName")))
                        .to("hwcloud-obs:getBucketMetadata?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient")
                        .log("Get bucket metadata successful")
                        .to("mock:bucket_metadata_result");
            }
        };
    }

    @Test
    public void testGetBucketMetadata() throws Exception {
        BucketMetadataInfoResult result
                = new BucketMetadataInfoResult(null, null, 12, null, null, null, "location-13", null, null);
        Mockito.when(mockClient.getBucketMetadata(Mockito.any(BucketMetadataInfoRequest.class))).thenReturn(result);

        MockEndpoint mock = getMockEndpoint("mock:bucket_metadata_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:bucket_metadata", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("{\"location\":\"location-13\",\"bucketType\":\"OBJECT\",\"maxAge\":12,\"statusCode\":0}",
                responseExchange.getIn().getBody(String.class));
    }
}
