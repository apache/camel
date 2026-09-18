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
package org.apache.camel.component.alibaba.ots;

import java.util.Map;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.CapacityUnit;
import com.alicloud.openservices.tablestore.model.ConsumedCapacity;
import com.alicloud.openservices.tablestore.model.PutRowRequest;
import com.alicloud.openservices.tablestore.model.PutRowResponse;
import com.alicloud.openservices.tablestore.model.Response;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PutRowTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("otsClient")
    SyncClient otsClient = mock(SyncClient.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putRow")
                        .to("alibaba-ots:putRow"
                            + "?endpoint=" + testConfiguration.getProperty("endpoint")
                            + "&instanceName=" + testConfiguration.getProperty("instanceName")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&otsClient=#otsClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testPutRow() throws Exception {
        PutRowResponse response = new PutRowResponse(
                new Response("req-put-row"),
                null,
                new ConsumedCapacity(new CapacityUnit(0, 1)));

        when(otsClient.putRow(any(PutRowRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        PutRowRequest request = new PutRowRequest();
        template.sendBody("direct:putRow", request);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Map<String, Object> body = exchange.getMessage().getBody(Map.class);
        assertThat(body)
                .containsEntry("requestId", "req-put-row")
                .containsKey("consumedCapacity");

        verify(otsClient).putRow(any(PutRowRequest.class));
    }
}
