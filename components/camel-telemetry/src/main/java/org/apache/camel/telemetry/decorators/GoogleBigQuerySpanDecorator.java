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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class GoogleBigQuerySpanDecorator extends AbstractSpanDecorator {

    static final String BIGQUERY_TABLE_ID = "tableId";
    static final String BIGQUERY_TABLE_SUFFIX = "tableSuffix";
    static final String BIGQUERY_PARTITION_DECORATOR = "partitionDecorator";
    static final String BIGQUERY_JOB_ID = "jobId";

    /**
     * Constants copied from {@link org.apache.camel.component.google.bigquery.GoogleBigQueryConstants}
     */
    static final String TABLE_ID = "CamelGoogleBigQueryTableId";
    static final String TABLE_SUFFIX = "CamelGoogleBigQueryTableSuffix";
    static final String PARTITION_DECORATOR = "CamelGoogleBigQueryPartitionDecorator";
    static final String JOB_ID = "CamelGoogleBigQueryJobId";

    @Override
    public String getComponent() {
        return "google-bigquery";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.google.bigquery.GoogleBigQueryComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String tableId = exchange.getIn().getHeader(TABLE_ID, String.class);
        if (tableId != null) {
            span.setTag(BIGQUERY_TABLE_ID, tableId);
        }

        String tableSuffix = exchange.getIn().getHeader(TABLE_SUFFIX, String.class);
        if (tableSuffix != null) {
            span.setTag(BIGQUERY_TABLE_SUFFIX, tableSuffix);
        }

        String partitionDecorator = exchange.getIn().getHeader(PARTITION_DECORATOR, String.class);
        if (partitionDecorator != null) {
            span.setTag(BIGQUERY_PARTITION_DECORATOR, partitionDecorator);
        }

        String jobId = exchange.getIn().getHeader(JOB_ID, String.class);
        if (jobId != null) {
            span.setTag(BIGQUERY_JOB_ID, jobId);
        }
    }

}
