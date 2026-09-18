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
package org.apache.camel.component.alibaba.sls;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.aliyun.sls20201230.Client;
import com.aliyun.sls20201230.models.ListLogStoresRequest;
import com.aliyun.sls20201230.models.ListLogStoresResponse;
import com.aliyun.sls20201230.models.ListLogStoresResponseBody;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.sls.constants.AlibabaSlsHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListLogStoresTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("slsClient")
    Client slsClient = mock(Client.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listLogStores")
                        .to("alibaba-sls:listLogStores"
                            + "?project=" + testConfiguration.getProperty("project")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&endpoint=" + testConfiguration.getProperty("endpoint")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&slsClient=#slsClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testListLogStores() throws Exception {
        ListLogStoresResponse response = new ListLogStoresResponse();
        response.setStatusCode(200);
        response.setHeaders(Collections.singletonMap("x-log-requestid", "req-789"));
        response.setBody(new ListLogStoresResponseBody()
                .setCount(2)
                .setTotal(2)
                .setLogstores(List.of("logstore-a", "logstore-b")));

        when(slsClient.listLogStores(
                eq(testConfiguration.getProperty("project")),
                any(ListLogStoresRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:listLogStores", null);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Map<String, Object> body = exchange.getMessage().getBody(Map.class);
        assertThat(body)
                .containsEntry("statusCode", 200)
                .containsEntry("count", 2)
                .containsEntry("total", 2)
                .containsEntry("logstores", List.of("logstore-a", "logstore-b"));
        assertThat(exchange.getMessage().getHeader(AlibabaSlsHeaders.STATUS_CODE)).isEqualTo(200);
        assertThat(exchange.getMessage().getHeader(AlibabaSlsHeaders.REQUEST_ID)).isEqualTo("req-789");

        verify(slsClient).listLogStores(
                eq(testConfiguration.getProperty("project")),
                any(ListLogStoresRequest.class));
    }

    @Test
    void testListLogStoresUsesDedicatedListOffset() throws Exception {
        ListLogStoresResponse response = new ListLogStoresResponse();
        response.setStatusCode(200);
        response.setBody(new ListLogStoresResponseBody().setCount(0).setTotal(0));

        when(slsClient.listLogStores(
                eq(testConfiguration.getProperty("project")),
                any(ListLogStoresRequest.class))).thenAnswer(invocation -> {
                    ListLogStoresRequest request = invocation.getArgument(1);
                    assertThat(request.getOffset()).isEqualTo(5);
                    return response;
                });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.send("direct:listLogStores", exchange -> {
            exchange.getIn().setHeader(AlibabaSlsHeaders.OFFSET, 99L);
            exchange.getIn().setHeader(AlibabaSlsHeaders.LIST_OFFSET, 5);
        });

        mock.assertIsSatisfied();
    }
}
