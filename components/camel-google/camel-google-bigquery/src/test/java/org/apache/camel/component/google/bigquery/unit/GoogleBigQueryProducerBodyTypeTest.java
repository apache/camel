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
package org.apache.camel.component.google.bigquery.unit;

import java.util.List;
import java.util.Map;

import com.google.cloud.bigquery.InsertAllRequest;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies what the producer reports for a body it cannot turn into rows.
 */
class GoogleBigQueryProducerBodyTypeTest extends BaseBigQueryTest {

    @Test
    void aBodyThatIsNotAMapOrAListIsReported() throws Exception {
        Exchange exchange = createExchangeWithBody("not a row");
        producer.process(exchange);

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot handle body type java.lang.String");
    }

    @Test
    void aMissingBodyIsReportedInsteadOfFailingWithANullPointer() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot handle body type null");
    }

    @Test
    void theRowsOfAGroupedExchangeAreInsertedTogether() throws Exception {
        Exchange grouped = grouped(createExchangeWithBody(Map.of("id", 1)), createExchangeWithBody(Map.of("id", 2)));
        producer.process(grouped);

        assertThat(grouped.getException()).isNull();
        ArgumentCaptor<InsertAllRequest> request = ArgumentCaptor.forClass(InsertAllRequest.class);
        verify(bigquery).insertAll(request.capture());
        assertThat(request.getValue().getRows()).hasSize(2);
    }

    @Test
    void aBodyThatIsNotARowFailsTheGroupedExchangeAndNoRowIsInserted() throws Exception {
        Exchange grouped = grouped(createExchangeWithBody(Map.of("id", 1)), createExchangeWithBody("not a row"));
        producer.process(grouped);

        assertThat(grouped.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Cannot handle body type java.lang.String")
                .hasMessageContaining("exchange 2 of 2 of the grouped exchange");
        verify(bigquery, never()).insertAll(any());
    }

    @Test
    void noRowIsInsertedWhenTheBadBodyGoesToAnotherTable() throws Exception {
        Exchange other = createExchangeWithBody(null);
        other.getIn().setHeader(GoogleBigQueryConstants.TABLE_ID, "otherTableId");
        Exchange grouped = grouped(createExchangeWithBody(Map.of("id", 1)), other);
        producer.process(grouped);

        assertThat(grouped.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Cannot handle body type null");
        // the row for the first table is not sent before the bad body is found
        verify(bigquery, never()).insertAll(any());
    }

    private Exchange grouped(Exchange... exchanges) {
        Exchange grouped = createExchangeWithBody(null);
        grouped.setProperty(ExchangePropertyKey.GROUPED_EXCHANGE, List.of(exchanges));
        return grouped;
    }
}
