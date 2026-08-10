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
package org.apache.camel.component.alibaba.oss;

import java.time.Instant;
import java.util.List;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.ListObjectsRequest;
import com.aliyun.sdk.service.oss2.models.ListObjectsResult;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.oss.constants.OSSProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ListObjectsTest extends CamelTestSupport {

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("ossClient")
    OSSClient mockClient = Mockito.mock(OSSClient.class);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list_objects")
                        .setProperty(OSSProperties.BUCKET_NAME, constant(testConfiguration.getProperty("bucketName")))
                        .to("alibaba-oss:listObjects?" +
                            "serviceKeys=#serviceKeys" +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ossClient=#ossClient")
                        .to("mock:list_objects_result");
            }
        };
    }

    @Test
    void testListObjects() throws Exception {
        ObjectSummary object1 = ObjectSummary.newBuilder()
                .key("Object 1")
                .size(100L)
                .eTag("etag-1")
                .lastModified(Instant.parse("2024-01-01T00:00:00Z"))
                .build();
        ObjectSummary object2 = ObjectSummary.newBuilder()
                .key("Object 2")
                .size(200L)
                .eTag("etag-2")
                .lastModified(Instant.parse("2024-01-02T00:00:00Z"))
                .build();

        ListObjectsResult listing = Mockito.mock(ListObjectsResult.class);
        Mockito.when(listing.contents()).thenReturn(List.of(object1, object2));
        Mockito.when(listing.isTruncated()).thenReturn(false);

        Mockito.when(mockClient.listObjects(Mockito.any(ListObjectsRequest.class))).thenReturn(listing);

        MockEndpoint mock = getMockEndpoint("mock:list_objects_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:list_objects", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertThat(responseExchange.getIn().getBody(String.class))
                .contains("\"objectKey\":\"Object 1\"")
                .contains("\"objectKey\":\"Object 2\"")
                .contains("\"bucketName\":\"dummy_bucket_name\"");
    }
}
