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
import java.util.HashMap;
import java.util.Map;

import com.aliyun.fc_open20210406.Client;
import com.aliyun.fc_open20210406.models.InvokeFunctionRequest;
import com.aliyun.fc_open20210406.models.InvokeFunctionResponse;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.fc.constants.FCHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Function Compute can answer invokeFunction for a function error with the status 200 and the X-Fc-Error-Type header.
 * The SDK gives the header names in lower case, as Headers.toMultimap of OkHttp returns them.
 */
class InvokeFunctionErrorTest extends CamelTestSupport {

    @BindToRegistry("fcClient")
    Client fcClient = mock(Client.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        String uri = "alibaba-fc:invokeFunction?serviceName=demo-service&functionName=demo-func&fcClient=#fcClient";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:invoke")
                        .to(uri);

                from("direct:invokeTwice")
                        .to(uri)
                        .to("mock:first")
                        .setBody(constant("{\"name\":\"camel\"}"))
                        .to(uri)
                        .to("mock:second");
            }
        };
    }

    @Test
    void aFunctionErrorSetsTheFunctionErrorHeaderAndTheExchangeDoesNotFail() throws Exception {
        when(fcClient.invokeFunction(anyString(), anyString(), any(InvokeFunctionRequest.class)))
                .thenReturn(response("UnhandledInvocationError", "{\"errorMessage\":\"boom\"}"));

        Exchange exchange = template.send("direct:invoke", e -> e.getMessage().setBody("{\"name\":\"camel\"}"));

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(FCHeaders.FUNCTION_ERROR)).isEqualTo("UnhandledInvocationError");
        assertThat(exchange.getMessage().getHeader(FCHeaders.STATUS_CODE)).isEqualTo(200);
    }

    @Test
    void aSucceedingCallRemovesTheHeaderOfAnEarlierFunctionError() throws Exception {
        when(fcClient.invokeFunction(anyString(), anyString(), any(InvokeFunctionRequest.class)))
                .thenReturn(response("HandledInvocationError", "{\"errorMessage\":\"boom\"}"),
                        response(null, "hello fc"));
        MockEndpoint first = getMockEndpoint("mock:first");
        first.expectedMessageCount(1);
        MockEndpoint second = getMockEndpoint("mock:second");
        second.expectedMessageCount(1);

        template.sendBody("direct:invokeTwice", "{\"name\":\"camel\"}");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(first.getExchanges().get(0).getMessage().getHeader(FCHeaders.FUNCTION_ERROR))
                .isEqualTo("HandledInvocationError");
        assertThat(second.getExchanges().get(0).getMessage().getHeaders()).doesNotContainKey(FCHeaders.FUNCTION_ERROR);
    }

    private static InvokeFunctionResponse response(String errorType, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-fc-request-id", "request-1");
        if (errorType != null) {
            headers.put("x-fc-error-type", errorType);
        }
        return new InvokeFunctionResponse()
                .setStatusCode(200)
                .setHeaders(headers)
                .setBody(body.getBytes(StandardCharsets.UTF_8));
    }
}
