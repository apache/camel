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
import com.obs.services.model.CreateBucketRequest;
import com.obs.services.model.ObsBucket;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateBucketTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CreateBucketTest.class.getName());

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
                from("direct:create_bucket")
                        .to("hwcloud-obs:createBucket?" +
                            "serviceKeys=#serviceKeys" +
                            "&bucketName=" + testConfiguration.getProperty("bucketName") +
                            "&bucketLocation=" + testConfiguration.getProperty("bucketLocation") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient")
                        .log("Create bucket successful")
                        .to("mock:create_bucket_result");
            }
        };
    }

    @Test
    public void testCreateBucket() throws Exception {
        ObsBucket bucket = new ObsBucket("New bucket", "location-3");
        Mockito.when(mockClient.createBucket(Mockito.any(CreateBucketRequest.class))).thenReturn(bucket);

        MockEndpoint mock = getMockEndpoint("mock:create_bucket_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:create_bucket", null);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("{\"bucketName\":\"New bucket\",\"location\":\"location-3\",\"metadata\":{},\"statusCode\":0}",
                responseExchange.getIn().getBody(String.class));
    }
}
