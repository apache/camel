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

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
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

class HeadObjectTest extends CamelTestSupport {

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
                from("direct:head_object")
                        .setProperty(OSSProperties.BUCKET_NAME, constant(testConfiguration.getProperty("bucketName")))
                        .setProperty(OSSProperties.OBJECT_NAME, constant(testConfiguration.getProperty("objectName")))
                        .to("alibaba-oss:headObject?" +
                            "serviceKeys=#serviceKeys" +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ossClient=#ossClient")
                        .to("mock:head_object_result");
            }
        };
    }

    @Test
    void testHeadObject() throws Exception {
        HeadObjectResult result = Mockito.mock(HeadObjectResult.class);
        Mockito.when(result.eTag()).thenReturn("eb733a00c0c9d336e65691a37ab54293");
        Mockito.when(result.contentLength()).thenReturn(1024L);
        Mockito.when(result.contentType()).thenReturn("text/plain");
        Mockito.when(result.contentMd5()).thenReturn("content-md5");
        Mockito.when(result.lastModified()).thenReturn("2024-01-01T00:00:00Z");
        Mockito.when(result.storageClass()).thenReturn("Standard");
        Mockito.when(result.metadata()).thenReturn(java.util.Map.of("custom", "value"));
        Mockito.when(result.statusCode()).thenReturn(200);
        Mockito.when(result.requestId()).thenReturn("request-id-456");

        Mockito.when(mockClient.headObject(Mockito.any(HeadObjectRequest.class))).thenReturn(result);

        MockEndpoint mock = getMockEndpoint("mock:head_object_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:head_object", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertThat(responseExchange.getIn().getBody(String.class))
                .contains("\"eTag\":\"eb733a00c0c9d336e65691a37ab54293\"")
                .contains("\"contentLength\":1024")
                .contains("\"contentType\":\"text/plain\"")
                .contains("\"storageClass\":\"Standard\"");
    }
}
