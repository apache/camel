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
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectResult;
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

class DeleteObjectTest extends CamelTestSupport {

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
                from("direct:delete_object")
                        .setHeader(OSSProperties.OBJECT_NAME, constant(testConfiguration.getProperty("objectName")))
                        .to("alibaba-oss:" + testConfiguration.getProperty("bucketName") + "?operation=deleteObject" +
                            "&serviceKeys=#serviceKeys" +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ossClient=#ossClient")
                        .to("mock:delete_object_result");
            }
        };
    }

    @Test
    void testDeleteObject() throws Exception {
        DeleteObjectResult result = Mockito.mock(DeleteObjectResult.class);
        Mockito.when(result.statusCode()).thenReturn(204);
        Mockito.when(result.requestId()).thenReturn("request-id-123");
        Mockito.when(result.deleteMarker()).thenReturn(false);
        Mockito.when(result.versionId()).thenReturn("version-1");

        Mockito.when(mockClient.deleteObject(Mockito.any(DeleteObjectRequest.class))).thenReturn(result);

        MockEndpoint mock = getMockEndpoint("mock:delete_object_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:delete_object", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = responseExchange.getIn().getBody(Map.class);
        assertThat(body)
                .containsEntry("statusCode", 204)
                .containsEntry("requestId", "request-id-123");
    }
}
