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
import org.apache.camel.telemetry.TagConstants;

public class AwsDdbSpanDecorator extends AbstractSpanDecorator {

    static final String DDB_TABLE_NAME = "tableName";
    static final String DDB_OPERATION = "operation";
    static final String DDB_INDEX_NAME = "indexName";
    static final String DDB_CONSUMED_CAPACITY = "consumedCapacity";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.ddb.Ddb2Constants}
     */
    static final String TABLE_NAME = "CamelAwsDdbTableName";
    static final String OPERATION = "CamelAwsDdbOperation";
    static final String INDEX_NAME = "CamelAwsDdbIndexName";
    static final String CONSUMED_CAPACITY = "CamelAwsDdbConsumedCapacity";

    @Override
    public String getComponent() {
        return "aws2-ddb";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.ddb.Ddb2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        span.setTag(TagConstants.DB_SYSTEM, "dynamodb");

        String tableName = exchange.getIn().getHeader(TABLE_NAME, String.class);
        if (tableName != null) {
            span.setTag(DDB_TABLE_NAME, tableName);
            span.setTag(TagConstants.DB_NAME, tableName);
        }

        Object operation = exchange.getIn().getHeader(OPERATION);
        if (operation != null) {
            span.setTag(DDB_OPERATION, operation.toString());
        }

        String indexName = exchange.getIn().getHeader(INDEX_NAME, String.class);
        if (indexName != null) {
            span.setTag(DDB_INDEX_NAME, indexName);
        }

        Double consumedCapacity = exchange.getIn().getHeader(CONSUMED_CAPACITY, Double.class);
        if (consumedCapacity != null) {
            span.setTag(DDB_CONSUMED_CAPACITY, consumedCapacity.toString());
        }
    }

}
