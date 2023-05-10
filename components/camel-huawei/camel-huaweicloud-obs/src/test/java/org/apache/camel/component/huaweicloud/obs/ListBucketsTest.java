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

import java.util.ArrayList;
import java.util.List;

import com.obs.services.ObsClient;
import com.obs.services.model.ListBucketsRequest;
import com.obs.services.model.ListBucketsResult;
import com.obs.services.model.ObsBucket;
import com.obs.services.model.Owner;
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

public class ListBucketsTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ListBucketsTest.class.getName());

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
                from("direct:list_buckets")
                        .to("hwcloud-obs:listBuckets?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient")
                        .log("List buckets successful")
                        .to("mock:list_buckets_result");
            }
        };
    }

    @Test
    public void testListBuckets() throws Exception {
        List<ObsBucket> buckets = new ArrayList<>();
        buckets.add(new ObsBucket("bucket1", "location-1"));
        buckets.add(new ObsBucket("bucket2", "location-2"));
        Owner owner = new Owner();
        ListBucketsResult response = new ListBucketsResult(buckets, owner, false, null, 10, null);
        Mockito.when(mockClient.listBucketsV2(Mockito.any(ListBucketsRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:list_buckets_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:list_buckets", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals(
                "[{\"bucketName\":\"bucket1\",\"location\":\"location-1\",\"metadata\":{},\"statusCode\":0},{\"bucketName\":\"bucket2\",\"location\":\"location-2\",\"metadata\":{},\"statusCode\":0}]",
                responseExchange.getIn().getBody(String.class));
    }
}
