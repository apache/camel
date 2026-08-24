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
package org.apache.camel.component.google.sheets.stream;

import java.util.List;
import java.util.Queue;

import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies the exchanges the stream consumer builds out of a batch-get response: one per value when the results are
 * split, one per range otherwise, and the range index counting the ranges of the response.
 */
class GoogleSheetsStreamConsumerRangeIndexTest {

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private GoogleSheetsStreamConsumer consumer(String query) throws Exception {
        if (context != null) {
            context.stop();
        }
        context = new DefaultCamelContext();
        // the endpoint is deliberately not started, so no google client is built
        GoogleSheetsStreamComponent component
                = context.getComponent("google-sheets-stream", GoogleSheetsStreamComponent.class);
        GoogleSheetsStreamEndpoint endpoint = (GoogleSheetsStreamEndpoint) component.createEndpoint(
                "google-sheets-stream://sheet1?clientId=id&clientSecret=secret&range=A1:B2,C1:D2" + query);
        return new GoogleSheetsStreamConsumer(endpoint, exchange -> {
        });
    }

    private static ValueRange range(String name, List<List<Object>> values) {
        return new ValueRange().setRange(name).setMajorDimension("ROWS").setValues(values);
    }

    private static List<Object> rangeIndexes(Queue<Exchange> exchanges) {
        return exchanges.stream()
                .map(e -> e.getIn().getHeader(GoogleSheetsStreamConstants.RANGE_INDEX))
                .toList();
    }

    @Test
    void splitResultsNumbersEveryRange() throws Exception {
        Queue<Exchange> exchanges = consumer("&splitResults=true").createExchanges(List.of(
                range("A1:B2", List.of(List.of("a1", "b1"), List.of("a2", "b2"))),
                range("C1:D2", List.of(List.of("c1", "d1")))));

        // one exchange per value, and the range index identifies which range the value came from
        assertThat(rangeIndexes(exchanges)).containsExactly(1, 1, 2);
        assertThat(exchanges.stream().map(e -> e.getIn().getHeader(GoogleSheetsStreamConstants.VALUE_INDEX)).toList())
                .containsExactly(1, 2, 1);
    }

    @Test
    void withoutSplitResultsEveryRangeIsOneExchange() throws Exception {
        Queue<Exchange> exchanges = consumer("").createExchanges(List.of(
                range("A1:B2", List.of(List.of("a1", "b1"))),
                range("C1:D2", List.of(List.of("c1", "d1")))));

        assertThat(rangeIndexes(exchanges)).containsExactly(1, 2);
    }

    @Test
    void aRangeWithoutValuesIsNotAFailure() throws Exception {
        // the sheets API omits the values field for a range that holds nothing
        GoogleSheetsStreamConsumer consumer = consumer("&splitResults=true");
        ValueRange empty = new ValueRange().setRange("A1:B2").setMajorDimension("ROWS");

        assertThatCode(() -> consumer.createExchanges(List.of(empty))).doesNotThrowAnyException();
        assertThat(consumer.createExchanges(List.of(empty))).isEmpty();
    }

    @Test
    void aRangeWithoutValuesIsNotAFailureWhenResultsAreNotSplit() throws Exception {
        GoogleSheetsStreamConsumer consumer = consumer("&maxResults=1");
        ValueRange empty = new ValueRange().setRange("A1:B2").setMajorDimension("ROWS");

        assertThatCode(() -> consumer.createExchanges(List.of(empty))).doesNotThrowAnyException();
    }
}
