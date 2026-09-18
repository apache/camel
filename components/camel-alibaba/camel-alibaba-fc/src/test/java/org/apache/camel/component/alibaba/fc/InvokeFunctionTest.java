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
package org.apache.camel.component.alibaba.fc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.aliyun.fc_open20210406.Client;
import com.aliyun.fc_open20210406.models.InvokeFunctionRequest;
import com.aliyun.fc_open20210406.models.InvokeFunctionResponse;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvokeFunctionTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("fcClient")
    Client fcClient = mock(Client.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:invoke")
                        .to("alibaba-fc:invokeFunction"
                            + "?serviceName=" + testConfiguration.getProperty("serviceName")
                            + "&functionName=" + testConfiguration.getProperty("functionName")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&fcClient=#fcClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testInvokeFunction() throws Exception {
        InvokeFunctionResponse response = new InvokeFunctionResponse();
        response.setStatusCode(200);
        response.setBody("hello fc".getBytes());

        when(fcClient.invokeFunction(
                eq(testConfiguration.getProperty("serviceName")),
                eq(testConfiguration.getProperty("functionName")),
                any(InvokeFunctionRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:invoke", "{\"name\":\"camel\"}");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = exchange.getMessage().getBody(Map.class);
        assertThat(body)
                .containsEntry("statusCode", 200)
                .containsEntry("body", "hello fc".getBytes(StandardCharsets.UTF_8));

        verify(fcClient).invokeFunction(
                eq(testConfiguration.getProperty("serviceName")),
                eq(testConfiguration.getProperty("functionName")),
                any(InvokeFunctionRequest.class));
    }
}
