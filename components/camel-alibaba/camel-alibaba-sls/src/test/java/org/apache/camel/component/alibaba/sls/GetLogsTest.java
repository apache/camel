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
import com.aliyun.sls20201230.models.GetLogsRequest;
import com.aliyun.sls20201230.models.GetLogsResponse;
import com.aliyun.sls20201230.models.GetLogsResponseBody;
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

class GetLogsTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("slsClient")
    Client slsClient = mock(Client.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getLogs")
                        .to("alibaba-sls:getLogs"
                            + "?project=" + testConfiguration.getProperty("project")
                            + "&logStoreName=" + testConfiguration.getProperty("logStoreName")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&endpoint=" + testConfiguration.getProperty("endpoint")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&query=*"
                            + "&from=1700000000"
                            + "&to=1700003600"
                            + "&line=100"
                            + "&slsClient=#slsClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testGetLogs() throws Exception {
        GetLogsResponse response = new GetLogsResponse();
        response.setStatusCode(200);
        response.setHeaders(Collections.singletonMap("x-log-requestid", "req-456"));
        GetLogsResponseBody logsResponseBody = new GetLogsResponseBody();
        logsResponseBody.setData(List.of(Map.of("message", "log line")));
        response.setBody(logsResponseBody);

        when(slsClient.getLogs(
                eq(testConfiguration.getProperty("project")),
                eq(testConfiguration.getProperty("logStoreName")),
                any(GetLogsRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:getLogs", null);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Map<String, Object> body = exchange.getMessage().getBody(Map.class);
        assertThat(body)
                .containsEntry("statusCode", 200)
                .containsKey("body");
        assertThat(body.get("body"))
                .isInstanceOf(GetLogsResponseBody.class);
        assertThat(((GetLogsResponseBody) body.get("body")).getData())
                .contains(Map.of("message", "log line"));
        assertThat(exchange.getMessage().getHeader(AlibabaSlsHeaders.STATUS_CODE)).isEqualTo(200);
        assertThat(exchange.getMessage().getHeader(AlibabaSlsHeaders.REQUEST_ID)).isEqualTo("req-456");

        verify(slsClient).getLogs(
                eq(testConfiguration.getProperty("project")),
                eq(testConfiguration.getProperty("logStoreName")),
                any(GetLogsRequest.class));
    }
}
