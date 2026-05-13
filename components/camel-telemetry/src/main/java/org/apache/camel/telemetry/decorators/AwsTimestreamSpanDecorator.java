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

public class AwsTimestreamSpanDecorator extends AbstractSpanDecorator {

    static final String TIMESTREAM_OPERATION = "operation";
    static final String TIMESTREAM_DATABASE_NAME = "databaseName";
    static final String TIMESTREAM_TABLE_NAME = "tableName";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.timestream.Timestream2Constants}
     */
    static final String OPERATION = "CamelAwsTimestreamOperation";
    static final String DATABASE_NAME = "CamelAwsTimestreamDatabaseName";
    static final String TABLE_NAME = "CamelAwsTimestreamTableName";

    @Override
    public String getComponent() {
        return "aws2-timestream";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.timestream.Timestream2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        span.setTag(TagConstants.DB_SYSTEM, "timestream");

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(TIMESTREAM_OPERATION, operation);
        }

        String databaseName = exchange.getIn().getHeader(DATABASE_NAME, String.class);
        if (databaseName != null) {
            span.setTag(TIMESTREAM_DATABASE_NAME, databaseName);
            span.setTag(TagConstants.DB_NAME, databaseName);
        }

        String tableName = exchange.getIn().getHeader(TABLE_NAME, String.class);
        if (tableName != null) {
            span.setTag(TIMESTREAM_TABLE_NAME, tableName);
        }
    }

}
