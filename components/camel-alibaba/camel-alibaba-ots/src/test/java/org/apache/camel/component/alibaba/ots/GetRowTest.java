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
import com.alicloud.openservices.tablestore.model.Column;
import com.alicloud.openservices.tablestore.model.ColumnValue;
import com.alicloud.openservices.tablestore.model.ConsumedCapacity;
import com.alicloud.openservices.tablestore.model.GetRowRequest;
import com.alicloud.openservices.tablestore.model.GetRowResponse;
import com.alicloud.openservices.tablestore.model.PrimaryKey;
import com.alicloud.openservices.tablestore.model.PrimaryKeyColumn;
import com.alicloud.openservices.tablestore.model.PrimaryKeyValue;
import com.alicloud.openservices.tablestore.model.Response;
import com.alicloud.openservices.tablestore.model.Row;
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

class GetRowTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("otsClient")
    SyncClient otsClient = mock(SyncClient.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getRow")
                        .to("alibaba-ots:getRow"
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
    void testGetRow() throws Exception {
        PrimaryKey primaryKey = new PrimaryKey(
                new PrimaryKeyColumn[] {
                        new PrimaryKeyColumn("pk", PrimaryKeyValue.fromString("id-1"))
                });
        Column[] columns = new Column[] {
                new Column("name", ColumnValue.fromString("camel"))
        };
        Row row = new Row(primaryKey, columns);
        GetRowResponse response = new GetRowResponse(
                new Response("req-get-row"),
                row,
                new ConsumedCapacity(new CapacityUnit(1, 0)));

        when(otsClient.getRow(any(GetRowRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        GetRowRequest request = new GetRowRequest();
        template.sendBody("direct:getRow", request);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertThat(exchange.getMessage().getBody(Map.class))
                .containsEntry("requestId", "req-get-row")
                .containsKey("row")
                .containsKey("consumedCapacity");

        verify(otsClient).getRow(any(GetRowRequest.class));
    }
}
