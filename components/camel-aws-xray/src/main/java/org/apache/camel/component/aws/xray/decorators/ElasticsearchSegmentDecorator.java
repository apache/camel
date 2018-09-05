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
package org.apache.camel.component.aws.xray.decorators;

import java.util.Map;

import com.amazonaws.xray.entities.Entity;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class ElasticsearchSegmentDecorator extends AbstractSegmentDecorator {

    public static final String ELASTICSARCH_DB_TYPE = "elasticsearch";
    public static final String ELASTICSEARCH_CLUSTER_TAG = "elasticsearch.cluster";

    @Override
    public String getComponent() {
        return "elasticsearch";
    }

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
        return queryParameters.containsKey("operation")
                ? queryParameters.get("operation")
                : super.getOperationName(exchange, endpoint);
    }

    @Override
    public void pre(Entity segment, Exchange exchange, Endpoint endpoint) {
        super.pre(segment, exchange, endpoint);

        segment.putMetadata("db.type", ELASTICSARCH_DB_TYPE);

        Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
        if (queryParameters.containsKey("indexName")) {
            segment.putMetadata("db.instance", queryParameters.get("indexName"));
        }

        String cluster = stripSchemeAndOptions(endpoint);
        if (null != cluster) {
            segment.putMetadata(ELASTICSEARCH_CLUSTER_TAG, cluster);
        }
    }
}
