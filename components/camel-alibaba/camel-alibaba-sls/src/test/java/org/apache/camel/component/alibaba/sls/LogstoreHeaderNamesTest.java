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

import com.aliyun.sls20201230.Client;
import com.aliyun.sls20201230.models.GetLogsRequest;
import com.aliyun.sls20201230.models.GetLogsResponse;
import com.aliyun.sls20201230.models.GetLogsResponseBody;
import com.aliyun.sls20201230.models.ListLogStoresRequest;
import com.aliyun.sls20201230.models.ListLogStoresResponse;
import com.aliyun.sls20201230.models.ListLogStoresResponseBody;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.sls.constants.AlibabaSlsHeaders;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The header of listLogStores must not share its name with the header of the Logstore of putLogs and getLogs, as Camel
 * header names are case-insensitive by default (CAMEL-27138).
 */
class LogstoreHeaderNamesTest extends CamelTestSupport {

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
                            + "&slsClient=#slsClient");
                from("direct:getLogs")
                        .to("alibaba-sls:getLogs"
                            + "?project=" + testConfiguration.getProperty("project")
                            + "&logStoreName=" + testConfiguration.getProperty("logStoreName")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&endpoint=" + testConfiguration.getProperty("endpoint")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&slsClient=#slsClient");
            }
        };
    }

    @Test
    void testListLogStoresDoesNotReadTheLogStoreOfPutLogsAndGetLogs() throws Exception {
        when(slsClient.listLogStores(anyString(), any(ListLogStoresRequest.class)))
                .thenReturn(new ListLogStoresResponse().setStatusCode(200).setBody(new ListLogStoresResponseBody()));

        Exchange result = template.send("direct:listLogStores",
                exchange -> exchange.getIn().setHeader(AlibabaSlsHeaders.LOG_STORE_NAME, "app-logstore"));

        assertThat(result.getException()).isNull();
        ArgumentCaptor<ListLogStoresRequest> request = ArgumentCaptor.forClass(ListLogStoresRequest.class);
        verify(slsClient).listLogStores(eq(testConfiguration.getProperty("project")), request.capture());
        assertThat(request.getValue().getLogstoreName()).isNull();
    }

    @Test
    void testGetLogsDoesNotReadTheLogstoreNameOfListLogStores() throws Exception {
        when(slsClient.getLogs(anyString(), anyString(), any(GetLogsRequest.class)))
                .thenReturn(new GetLogsResponse().setStatusCode(200).setBody(new GetLogsResponseBody()));

        Exchange result = template.send("direct:getLogs",
                exchange -> exchange.getIn().setHeader(AlibabaSlsHeaders.LOGSTORE_NAME, "other-logstore"));

        assertThat(result.getException()).isNull();
        verify(slsClient).getLogs(
                eq(testConfiguration.getProperty("project")),
                eq(testConfiguration.getProperty("logStoreName")),
                any(GetLogsRequest.class));
    }

    @Test
    void testListLogStoresReadsItsLogstoreNameHeader() throws Exception {
        when(slsClient.listLogStores(anyString(), any(ListLogStoresRequest.class)))
                .thenReturn(new ListLogStoresResponse().setStatusCode(200).setBody(new ListLogStoresResponseBody()));

        Exchange result = template.send("direct:listLogStores",
                exchange -> exchange.getIn().setHeader(AlibabaSlsHeaders.LOGSTORE_NAME, "app"));

        assertThat(result.getException()).isNull();
        ArgumentCaptor<ListLogStoresRequest> request = ArgumentCaptor.forClass(ListLogStoresRequest.class);
        verify(slsClient).listLogStores(eq(testConfiguration.getProperty("project")), request.capture());
        assertThat(request.getValue().getLogstoreName()).isEqualTo("app");
    }
}
