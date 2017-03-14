/**
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
package org.apache.camel.opentracing.decorators;

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class ElasticsearchSpanDecorator extends AbstractSpanDecorator {

    public static final String ELASTICSEARCH_DB_TYPE = "elasticsearch";

    public static final String ELASTICSEARCH_CLUSTER_TAG = "elasticsearch.cluster";

    @Override
    public String getComponent() {
        return "elasticsearch";
    }

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
        return queryParameters.containsKey("operation") ? queryParameters.get("operation")
                : super.getOperationName(exchange, endpoint);
    }

    @Override
    public void pre(Span span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);

        span.setTag(Tags.DB_TYPE.getKey(), ELASTICSEARCH_DB_TYPE);

        Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
        if (queryParameters.containsKey("indexName")) {
            span.setTag(Tags.DB_INSTANCE.getKey(), queryParameters.get("indexName"));
        }

        String cluster = stripSchemeAndOptions(endpoint);
        if (cluster != null) {
            span.setTag(ELASTICSEARCH_CLUSTER_TAG, cluster);
        }
    }

}
