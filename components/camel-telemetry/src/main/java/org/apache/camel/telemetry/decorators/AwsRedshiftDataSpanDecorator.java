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

public class AwsRedshiftDataSpanDecorator extends AbstractSpanDecorator {

    static final String REDSHIFTDATA_OPERATION = "operation";
    static final String REDSHIFTDATA_CLUSTER_IDENTIFIER = "clusterIdentifier";
    static final String REDSHIFTDATA_DATABASE = "database";
    static final String REDSHIFTDATA_STATEMENT_NAME = "statementName";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.redshift.data.RedshiftData2Constants}
     */
    static final String OPERATION = "CamelAwsRedshiftDataOperation";
    static final String CLUSTER_IDENTIFIER = "CamelAwsRedshiftDataClusterIdentifier";
    static final String DATABASE = "CamelAwsRedshiftDataDatabase";
    static final String STATEMENT_NAME = "CamelAwsRedshiftDataStatementName";

    @Override
    public String getComponent() {
        return "aws2-redshift-data";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.redshift.data.RedshiftData2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        span.setTag(TagConstants.DB_SYSTEM, "redshift");

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(REDSHIFTDATA_OPERATION, operation);
        }

        String clusterIdentifier = exchange.getIn().getHeader(CLUSTER_IDENTIFIER, String.class);
        if (clusterIdentifier != null) {
            span.setTag(REDSHIFTDATA_CLUSTER_IDENTIFIER, clusterIdentifier);
        }

        String database = exchange.getIn().getHeader(DATABASE, String.class);
        if (database != null) {
            span.setTag(REDSHIFTDATA_DATABASE, database);
            span.setTag(TagConstants.DB_NAME, database);
        }

        String statementName = exchange.getIn().getHeader(STATEMENT_NAME, String.class);
        if (statementName != null) {
            span.setTag(REDSHIFTDATA_STATEMENT_NAME, statementName);
        }
    }

}
