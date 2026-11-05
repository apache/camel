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
package org.apache.camel.component.google.bigquery.unit.sql;

import com.google.cloud.bigquery.JobStatistics.QueryStatistics.StatementType;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Verifies the body the producer gives for a statement that is not a SELECT: the number of rows a DML statement
 * affected, and no body for a statement that has no such number, such as DDL.
 */
class GoogleBigQuerySQLProducerStatementTest extends GoogleBigQuerySQLProducerBaseTest {

    @Test
    void aDdlStatementGivesNoBody() throws Exception {
        sql = "create table testDatasetId.newTable (id INT64)";
        setupBigqueryMock();
        when(statistics.getStatementType()).thenReturn(StatementType.CREATE_TABLE);
        // BigQuery gives no number of affected rows for DDL
        when(statistics.getNumDmlAffectedRows()).thenReturn(null);
        producer = createAndStartProducer();

        Exchange exchange = createExchangeWithBody("the body before");
        producer.process(exchange);

        assertNull(exchange.getMessage().getBody());
    }

    @Test
    void aDmlStatementThatChangesNoRowGivesZero() throws Exception {
        sql = "delete from testDatasetId.testTableId where false";
        setupBigqueryMock();
        when(statistics.getStatementType()).thenReturn(StatementType.DELETE);
        when(statistics.getNumDmlAffectedRows()).thenReturn(0L);
        producer = createAndStartProducer();

        Exchange exchange = createExchangeWithBody("the body before");
        producer.process(exchange);

        assertEquals(Long.valueOf(0L), exchange.getMessage().getBody());
    }
}
