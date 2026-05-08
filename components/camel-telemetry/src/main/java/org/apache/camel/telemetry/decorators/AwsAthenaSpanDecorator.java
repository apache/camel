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

public class AwsAthenaSpanDecorator extends AbstractSpanDecorator {

    static final String ATHENA_OPERATION = "operation";
    static final String ATHENA_DATABASE = "database";
    static final String ATHENA_QUERY_EXECUTION_ID = "queryExecutionId";
    static final String ATHENA_WORK_GROUP = "workGroup";
    static final String ATHENA_QUERY_EXECUTION_STATE = "queryExecutionState";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.athena.Athena2Constants}
     */
    static final String OPERATION = "CamelAwsAthenaOperation";
    static final String DATABASE = "CamelAwsAthenaDatabase";
    static final String QUERY_EXECUTION_ID = "CamelAwsAthenaQueryExecutionId";
    static final String WORK_GROUP = "CamelAwsAthenaWorkGroup";
    static final String QUERY_EXECUTION_STATE = "CamelAwsAthenaQueryExecutionState";

    @Override
    public String getComponent() {
        return "aws2-athena";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.athena.Athena2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        span.setTag(TagConstants.DB_SYSTEM, "athena");

        Object operation = exchange.getIn().getHeader(OPERATION);
        if (operation != null) {
            span.setTag(ATHENA_OPERATION, operation.toString());
        }

        String database = exchange.getIn().getHeader(DATABASE, String.class);
        if (database != null) {
            span.setTag(ATHENA_DATABASE, database);
            span.setTag(TagConstants.DB_NAME, database);
        }

        String queryExecutionId = exchange.getIn().getHeader(QUERY_EXECUTION_ID, String.class);
        if (queryExecutionId != null) {
            span.setTag(ATHENA_QUERY_EXECUTION_ID, queryExecutionId);
        }

        String workGroup = exchange.getIn().getHeader(WORK_GROUP, String.class);
        if (workGroup != null) {
            span.setTag(ATHENA_WORK_GROUP, workGroup);
        }

        Object queryExecutionState = exchange.getIn().getHeader(QUERY_EXECUTION_STATE);
        if (queryExecutionState != null) {
            span.setTag(ATHENA_QUERY_EXECUTION_STATE, queryExecutionState.toString());
        }
    }

}
