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

package org.apache.camel.component.google.bigquery.integration.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.assertj.core.api.InstanceOfAssertFactories.iterator;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.apache.camel.component.google.bigquery.integration.BigQueryITSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf(
        value = "org.apache.camel.component.google.bigquery.integration.BigQueryITSupport#hasCredentials",
        disabledReason = "Credentials were not provided")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SqlQuerySelectAndStreamIT extends BigQueryITSupport {

    @EndpointInject("mock:selectResult")
    private MockEndpoint selectResult;

    @EndpointInject("mock:streamResult")
    private MockEndpoint streamResult;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                String baseUri = "google-bigquery-sql:{{project.id}}:classpath:sql/select.sql";

                from("direct:selectListPaged").toD(baseUri + "?pageSize=1").to("mock:selectResult");

                from("direct:streamList")
                        .toD(baseUri + "?outputType=STREAM_LIST&pageSize=1")
                        .to("mock:streamResult");
            }
        };
    }

    @Test
    public void testStreamList() throws Exception {
        streamResult.reset();
        streamResult.expectedMessageCount(1);
        template.sendBody("direct:streamList", null);
        streamResult.assertIsSatisfied();

        Message streamListMessage = streamResult.getExchanges().get(0).getMessage();
        Object body = streamListMessage.getBody();

        assertThat(body).asInstanceOf(iterator(Map.class)).satisfies(iterator -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            iterator.forEachRemaining(rows::add);

            assertThat(rows).hasSize(2);

            for (int i = 0; i < 2; i++) {
                assertRowContent(rows.get(i), i + 1);
            }
        });
    }

    @Test
    public void testSelectListPagination() throws Exception {
        selectResult.reset();
        selectResult.expectedMessageCount(1);
        template.sendBody("direct:selectListPaged", null);
        selectResult.assertIsSatisfied();

        Message firstPageMessage = selectResult.getExchanges().get(0).getMessage();
        Object firstPageRows = firstPageMessage.getBody();
        assertThat(firstPageRows)
                .asInstanceOf(list(Map.class))
                .hasSize(1)
                .first(map(String.class, Object.class))
                .satisfies(row -> assertRowContent(row, 1));

        // Get pagination info
        String nextPageToken = firstPageMessage.getHeader(GoogleBigQueryConstants.NEXT_PAGE_TOKEN, String.class);
        Object jobId = firstPageMessage.getHeader(GoogleBigQueryConstants.JOB_ID);

        assertThat(nextPageToken).isNotNull();
        assertThat(jobId).isNotNull();

        // Query second page using pageToken
        selectResult.reset();
        selectResult.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(GoogleBigQueryConstants.PAGE_TOKEN, nextPageToken);
        headers.put(GoogleBigQueryConstants.JOB_ID, jobId);
        template.sendBodyAndHeaders("direct:selectListPaged", null, headers);
        selectResult.assertIsSatisfied();

        Message secondPageMessage = selectResult.getExchanges().get(0).getMessage();
        Object secondPageRows = secondPageMessage.getBody(List.class);
        assertThat(secondPageRows)
                .asInstanceOf(list(Map.class))
                .hasSize(1)
                .first(map(String.class, Object.class))
                .satisfies(row -> assertRowContent(row, 2));
    }

    private void assertRowContent(Map<String, Object> row, int seq) {
        assertThat(row.get("address")).asInstanceOf(MAP).containsEntry("city", "City" + seq);

        assertThat(row.get("tags")).asInstanceOf(LIST).contains("tag" + seq);

        assertThat(row.get("contacts"))
                .asInstanceOf(LIST)
                .hasSize(2)
                .first(MAP)
                .containsEntry("value", "user" + seq + "ATexample.com");
    }
}
