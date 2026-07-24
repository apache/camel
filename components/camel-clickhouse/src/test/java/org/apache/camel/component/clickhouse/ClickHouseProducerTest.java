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
package org.apache.camel.component.clickhouse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseFormat;
import org.apache.camel.CamelContext;
import org.apache.camel.WrappedFile;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickHouseProducerTest extends CamelTestSupport {

    private final Client client = mock(Client.class);
    private final InsertResponse insertResponse = mock(InsertResponse.class);
    private final QueryResponse queryResponse = mock(QueryResponse.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ClickHouseComponent component = new ClickHouseComponent();
        component.setClient(client);
        context.addComponent("clickhouse", component);
        return context;
    }

    @BeforeEach
    void setupMocks() {
        when(insertResponse.getWrittenRows()).thenReturn(3L);
        when(client.insert(anyString(), any(InputStream.class), any(ClickHouseFormat.class), any(InsertSettings.class)))
                .thenReturn(CompletableFuture.completedFuture(insertResponse));
        when(client.insert(anyString(), anyList(), any(InsertSettings.class)))
                .thenReturn(CompletableFuture.completedFuture(insertResponse));

        when(queryResponse.getReadRows()).thenReturn(5L);
        when(queryResponse.getInputStream())
                .thenReturn(new ByteArrayInputStream("id,name\n1,alice\n".getBytes(StandardCharsets.UTF_8)));
        when(client.query(anyString(), any(QuerySettings.class)))
                .thenReturn(CompletableFuture.completedFuture(queryResponse));

        when(client.ping()).thenReturn(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:insert")
                        .to("clickhouse://analytics.events?operation=insert&format=JSONEachRow")
                        .to("mock:result");
                from("direct:insertRowBinary")
                        .to("clickhouse://analytics.events?operation=insert&format=RowBinary")
                        .to("mock:result");
                from("direct:insertBatched")
                        .to("clickhouse://analytics.events?operation=insert&format=JSONEachRow&batchSize=2")
                        .to("mock:result");
                from("direct:query")
                        .to("clickhouse://analytics?operation=query&format=CSV")
                        .to("mock:result");
                from("direct:ping")
                        .to("clickhouse://analytics?operation=ping")
                        .to("mock:result");
                from("direct:default")
                        .to("clickhouse://analytics.events")
                        .to("mock:result");
            }
        };
    }

    @Test
    void insertStreamsStringBodyWithConfiguredFormat() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:insert", "{\"id\":1,\"name\":\"alice\"}");

        MockEndpoint.assertIsSatisfied(context);

        ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ClickHouseFormat> formatCaptor = ArgumentCaptor.forClass(ClickHouseFormat.class);
        verify(client).insert(tableCaptor.capture(), any(InputStream.class), formatCaptor.capture(),
                any(InsertSettings.class));

        assertThat(tableCaptor.getValue()).isEqualTo("events");
        assertThat(formatCaptor.getValue()).isEqualTo(ClickHouseFormat.JSONEachRow);
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(ClickHouseConstants.WRITTEN_ROWS))
                .isEqualTo(3L);
    }

    @Test
    void insertStreamsByteArrayBody() throws Exception {
        template.sendBody("direct:insertRowBinary", "row-binary-bytes".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<ClickHouseFormat> formatCaptor = ArgumentCaptor.forClass(ClickHouseFormat.class);
        verify(client).insert(anyString(), any(InputStream.class), formatCaptor.capture(), any(InsertSettings.class));
        assertThat(formatCaptor.getValue()).isEqualTo(ClickHouseFormat.RowBinary);
    }

    @Test
    void insertUsesNativeListApiForListBodies() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of("id", 1, "name", "alice"), Map.of("id", 2, "name", "bob"));

        template.sendBody("direct:insert", rows);

        verify(client).insert(anyString(), anyList(), any(InsertSettings.class));
    }

    @Test
    void insertSplitsListIntoBatchesWhenBatchSizeSet() throws Exception {
        // batchSize=2 with 5 rows -> 3 insert calls (2 + 2 + 1)
        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1), Map.of("id", 2), Map.of("id", 3), Map.of("id", 4), Map.of("id", 5));

        template.sendBody("direct:insertBatched", rows);

        ArgumentCaptor<List> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).insert(anyString(), batchCaptor.capture(), any(InsertSettings.class));

        List<List> batches = batchCaptor.getAllValues();
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(2);
        assertThat(batches.get(1)).hasSize(2);
        assertThat(batches.get(2)).hasSize(1);
    }

    @Test
    void insertStreamsWrappedFileBody() throws Exception {
        File file = File.createTempFile("clickhouse-", ".json");
        file.deleteOnExit();
        java.nio.file.Files.writeString(file.toPath(), "{\"id\":1,\"name\":\"alice\"}");

        WrappedFile<File> wrappedFile = new WrappedFile<>() {
            @Override
            public File getFile() {
                return file;
            }

            @Override
            public Object getBody() {
                return file;
            }

            @Override
            public long getFileLength() {
                return file.length();
            }
        };

        template.sendBody("direct:insert", wrappedFile);

        verify(client).insert(anyString(), any(InputStream.class), any(ClickHouseFormat.class), any(InsertSettings.class));
    }

    @Test
    void queryReturnsResultBodyAndReadRowsHeader() throws Exception {
        Object result = template.requestBody("direct:query", "SELECT * FROM analytics.events");

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).contains("alice");

        MockEndpoint mock = getMockEndpoint("mock:result");
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(ClickHouseConstants.READ_ROWS)).isEqualTo(5L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).query(sqlCaptor.capture(), any(QuerySettings.class));
        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM analytics.events");
    }

    @Test
    void pingReturnsBooleanBodyAndHeader() throws Exception {
        Object result = template.requestBody("direct:ping", (Object) null);

        assertThat(result).isEqualTo(true);
        verify(client).ping();

        MockEndpoint mock = getMockEndpoint("mock:result");
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(ClickHouseConstants.PING_OK)).isEqualTo(true);
    }

    @Test
    void operationHeaderOverridesEndpointOperation() throws Exception {
        // endpoint is configured for insert, but the header forces a ping
        Object result = template.requestBodyAndHeader("direct:default", null,
                ClickHouseConstants.OPERATION, "ping");

        assertThat(result).isEqualTo(true);
        verify(client).ping();
    }

    @Test
    void tableAndFormatHeadersOverrideEndpoint() throws Exception {
        template.sendBodyAndHeaders("direct:insert", "{\"id\":1}", Map.of(
                ClickHouseConstants.TABLE, "overridden",
                ClickHouseConstants.FORMAT, "TSV"));

        ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ClickHouseFormat> formatCaptor = ArgumentCaptor.forClass(ClickHouseFormat.class);
        verify(client).insert(tableCaptor.capture(), any(InputStream.class), formatCaptor.capture(),
                any(InsertSettings.class));

        assertThat(tableCaptor.getValue()).isEqualTo("overridden");
        assertThat(formatCaptor.getValue()).isEqualTo(ClickHouseFormat.TSV);
    }

    @Test
    void queryWithoutSqlBodyFails() {
        assertThat(template.request("direct:query", exchange -> exchange.getIn().setBody(null))
                .getException())
                .isInstanceOf(ClickHouseException.class)
                .hasMessageContaining("SQL query");
    }
}
