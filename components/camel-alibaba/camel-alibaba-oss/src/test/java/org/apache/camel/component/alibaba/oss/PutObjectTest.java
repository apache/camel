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

import java.util.Map;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
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

class PutObjectTest extends CamelTestSupport {

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
                from("direct:put_object")
                        .setBody(constant("a test string"))
                        .setHeader(OSSProperties.OBJECT_NAME, constant("string_file.txt"))
                        .to("alibaba-oss:test-bucket?operation=putObject" +
                            "&serviceKeys=#serviceKeys" +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ossClient=#ossClient")
                        .to("mock:put_object_result");
            }
        };
    }

    @Test
    void putObjectStringTest() throws Exception {
        PutObjectResult putObjectResult = Mockito.mock(PutObjectResult.class);
        Mockito.when(putObjectResult.eTag()).thenReturn("eb733a00c0c9d336e65691a37ab54293");
        Mockito.when(putObjectResult.contentMd5()).thenReturn("content-md5");
        Mockito.when(putObjectResult.versionId()).thenReturn("version-xxx");
        Mockito.when(putObjectResult.statusCode()).thenReturn(200);

        Mockito.when(mockClient.putObject(Mockito.any(PutObjectRequest.class)))
                .thenReturn(putObjectResult);

        MockEndpoint mock = getMockEndpoint("mock:put_object_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:put_object", "sample file content");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = responseExchange.getIn().getBody(Map.class);
        assertThat(body)
                .containsEntry("bucketName", "test-bucket")
                .containsEntry("objectKey", "string_file.txt")
                .containsEntry("eTag", "eb733a00c0c9d336e65691a37ab54293");
    }
}
