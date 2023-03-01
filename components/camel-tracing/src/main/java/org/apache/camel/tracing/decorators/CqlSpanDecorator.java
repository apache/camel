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
package org.apache.camel.tracing.decorators;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.Tag;

public class CqlSpanDecorator extends AbstractSpanDecorator {

    public static final String CASSANDRA_DB_TYPE = "cassandra";

    protected static final String CAMEL_CQL_QUERY = "CamelCqlQuery";

    @Override
    public String getComponent() {
        return "cql";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.cassandra.CassandraComponent";
    }

    @Override
    public void pre(SpanAdapter span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);
        span.setLowCardinalityTag(Tag.DB_TYPE, CASSANDRA_DB_TYPE);
        URI uri = URI.create(endpoint.getEndpointUri());
        if (uri.getPath() != null && uri.getPath().length() > 0) {
            // Strip leading '/' from path
            span.setTag(Tag.DB_INSTANCE, uri.getPath().substring(1));
        }

        String cql = exchange.getIn().getHeader(CAMEL_CQL_QUERY, String.class);
        if (cql != null) {
            span.setTag(Tag.DB_STATEMENT, cql);
        } else {
            Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
            if (queryParameters.containsKey("cql")) {
                span.setTag(Tag.DB_STATEMENT, queryParameters.get("cql"));
            }
        }
    }

}
