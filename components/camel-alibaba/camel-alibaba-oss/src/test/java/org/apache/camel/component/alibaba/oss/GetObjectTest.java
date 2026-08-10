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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.oss.constants.OSSHeaders;
import org.apache.camel.component.alibaba.oss.constants.OSSProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class GetObjectTest extends CamelTestSupport {

    TestConfiguration testConfiguration = new TestConfiguration();

    String bucketName = "test-bucket";
    String objectName = "test_file.txt";

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
                from("direct:get_object")
                        .setProperty(OSSProperties.BUCKET_NAME, constant(bucketName))
                        .setProperty(OSSProperties.OBJECT_NAME, constant(objectName))
                        .to("alibaba-oss:getObject?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ossClient=#ossClient")
                        .to("mock:get_object_result");
            }
        };
    }

    @Test
    void testGetObject() throws Exception {
        GetObjectResult response = Mockito.mock(GetObjectResult.class);
        InputStream stream = new ByteArrayInputStream("hello oss".getBytes());
        Mockito.when(response.body()).thenReturn(stream);
        Mockito.when(response.contentLength()).thenReturn(9L);
        Mockito.when(response.contentType()).thenReturn("text/plain");
        Mockito.when(response.eTag()).thenReturn("eb733a00c0c9d336e65691a37ab54293");
        Mockito.when(response.contentMd5()).thenReturn("63M6AMDJ0zbmVpGjerVCkw==");
        Mockito.when(response.lastModified()).thenReturn("2024-01-01T00:00:00Z");

        Mockito.when(mockClient.getObject(Mockito.any(GetObjectRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:get_object_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:get_object", "dummy");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertThat(responseExchange.getIn().getHeader(Exchange.CONTENT_LENGTH)).isEqualTo(9L);
        assertThat(responseExchange.getIn().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/plain");
        assertThat(responseExchange.getIn().getHeader(OSSHeaders.ETAG)).isEqualTo("eb733a00c0c9d336e65691a37ab54293");
        assertThat(responseExchange.getIn().getHeader(OSSHeaders.CONTENT_MD5)).isEqualTo("63M6AMDJ0zbmVpGjerVCkw==");
        assertThat(responseExchange.getIn().getHeader(OSSHeaders.LAST_MODIFIED)).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(responseExchange.getIn().getHeader(OSSHeaders.BUCKET_NAME)).isEqualTo(bucketName);
        assertThat(responseExchange.getIn().getHeader(OSSHeaders.OBJECT_KEY)).isEqualTo(objectName);
        assertThat(responseExchange.getIn().getHeader(Exchange.FILE_NAME)).isEqualTo(objectName);
        assertThat(responseExchange.getIn().getBody(byte[].class)).isEqualTo("hello oss".getBytes());
    }
}
