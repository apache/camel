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

import java.time.Instant;
import java.util.List;

import com.aliyun.sls20201230.Client;
import com.aliyun.sls20201230.models.LogContent;
import com.aliyun.sls20201230.models.LogGroup;
import com.aliyun.sls20201230.models.LogItem;
import com.aliyun.sls20201230.models.PutLogsRequest;
import com.aliyun.sls20201230.models.PutLogsResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The putLogs example of the docs: a bean builds the LogGroup of the text of the message, which putLogs writes. A text
 * body, which the earlier example set, fails.
 */
class PutLogsDocExampleTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final Client slsClient = mock(Client.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        when(slsClient.putLogs(any(), any(), any(PutLogsRequest.class))).thenReturn(new PutLogsResponse().setStatusCode(200));
        context.getRegistry().bind("slsClient", slsClient);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String putLogs = "alibaba-sls:putLogs"
                         + "?project=" + testConfiguration.getProperty("project")
                         + "&logStoreName=" + testConfiguration.getProperty("logStoreName")
                         + "&region=" + testConfiguration.getProperty("region")
                         + "&endpoint=" + testConfiguration.getProperty("endpoint")
                         + "&accessKey=" + testConfiguration.getProperty("accessKey")
                         + "&secretKey=" + testConfiguration.getProperty("secretKey")
                         + "&slsClient=#slsClient";
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the example of the docs
                from("direct:start")
                        .bean(SlsLogs.class, "toLogGroup")
                        .to(putLogs);

                // the earlier example of the docs, which set a text body
                from("direct:text")
                        .setBody(simple("${body}"))
                        .to(putLogs);
            }
        };
    }

    @Test
    void beanOfTheDocsBuildsTheLogGroup() throws Exception {
        long before = Instant.now().getEpochSecond();

        template.sendBody("direct:start", "hello sls");

        ArgumentCaptor<PutLogsRequest> request = ArgumentCaptor.forClass(PutLogsRequest.class);
        verify(slsClient).putLogs(
                eq(testConfiguration.getProperty("project")), eq(testConfiguration.getProperty("logStoreName")),
                request.capture());
        LogGroup logGroup = request.getValue().getBody();
        assertThat(logGroup.getTopic()).isEqualTo("camel");
        assertThat(logGroup.getLogItems()).hasSize(1);
        LogItem log = logGroup.getLogItems().get(0);
        assertThat(log.getTime()).isBetween((int) before, (int) Instant.now().getEpochSecond());
        assertThat(log.getContents()).hasSize(1);
        assertThat(log.getContents().get(0).getKey()).isEqualTo("message");
        assertThat(log.getContents().get(0).getValue()).isEqualTo("hello sls");
    }

    @Test
    void textBodyFails() throws Exception {
        assertThatThrownBy(() -> template.sendBody("direct:text", "hello sls"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LogGroup or PutLogsRequest");
        verify(slsClient, never()).putLogs(any(), any(), any(PutLogsRequest.class));
    }

    /**
     * The bean of the docs.
     */
    public static class SlsLogs {

        public LogGroup toLogGroup(String message) {
            LogItem log = new LogItem()
                    .setTime((int) Instant.now().getEpochSecond())
                    .setContents(List.of(new LogContent().setKey("message").setValue(message)));
            return new LogGroup().setTopic("camel").setLogItems(List.of(log));
        }
    }
}
