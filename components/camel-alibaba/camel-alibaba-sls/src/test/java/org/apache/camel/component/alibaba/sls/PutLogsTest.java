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
import com.aliyun.sls20201230.models.LogContent;
import com.aliyun.sls20201230.models.LogGroup;
import com.aliyun.sls20201230.models.LogItem;
import com.aliyun.sls20201230.models.PutLogsRequest;
import com.aliyun.sls20201230.models.PutLogsResponse;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.sls.constants.SLSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PutLogsTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("slsClient")
    Client slsClient = mock(Client.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putLogs")
                        .to("alibaba-sls:putLogs"
                            + "?project=" + testConfiguration.getProperty("project")
                            + "&logStoreName=" + testConfiguration.getProperty("logStoreName")
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
    void testPutLogsWithLogGroup() throws Exception {
        PutLogsResponse response = new PutLogsResponse();
        response.setStatusCode(200);
        response.setHeaders(Collections.singletonMap("x-log-requestid", "req-123"));

        when(slsClient.putLogs(
                eq(testConfiguration.getProperty("project")),
                eq(testConfiguration.getProperty("logStoreName")),
                any(PutLogsRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        LogGroup logGroup = new LogGroup()
                .setTopic("test-topic")
                .setLogItems(List.of(new LogItem()
                        .setTime(1700000000)
                        .setContents(List.of(new LogContent().setKey("message").setValue("hello sls")))));
        template.sendBody("direct:putLogs", logGroup);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertThat(exchange.getMessage().getBody(Map.class))
                .containsEntry("statusCode", 200);
        assertThat(exchange.getMessage().getHeader(SLSHeaders.STATUS_CODE)).isEqualTo(200);
        assertThat(exchange.getMessage().getHeader(SLSHeaders.REQUEST_ID)).isEqualTo("req-123");

        verify(slsClient).putLogs(
                eq(testConfiguration.getProperty("project")),
                eq(testConfiguration.getProperty("logStoreName")),
                any(PutLogsRequest.class));
    }
}
