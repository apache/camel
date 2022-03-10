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
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.obs.constants.OBSProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CheckBucketTest extends CamelTestSupport {

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
                from("direct:check_bucket")
                        .to("hwcloud-obs:checkBucketExists?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&bucketName=" + testConfiguration.getProperty("bucketName") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient")
                        .log("Check bucket exists successful")
                        .to("mock:check_bucket_result");
            }
        };
    }

    @Test
    public void testCheckBucketExists() throws Exception {
        Mockito.when(mockClient.headBucket(Mockito.any(String.class))).thenReturn(true);

        MockEndpoint mock = getMockEndpoint("mock:check_bucket_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:check_bucket", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertTrue(responseExchange.getProperty(OBSProperties.BUCKET_EXISTS, boolean.class));
    }
}
