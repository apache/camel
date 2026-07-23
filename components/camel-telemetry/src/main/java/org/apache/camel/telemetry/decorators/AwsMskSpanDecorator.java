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

public class AwsMskSpanDecorator extends AbstractSpanDecorator {

    static final String MSK_OPERATION = "operation";
    static final String MSK_CLUSTER_NAME = "clusterName";
    static final String MSK_CLUSTER_ARN = "clusterArn";
    static final String MSK_CLUSTER_STATE = "clusterState";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.msk.MSK2Constants}
     */
    static final String OPERATION = "CamelAwsMSKOperation";
    static final String CLUSTER_NAME = "CamelAwsMSKClusterName";
    static final String CLUSTER_ARN = "CamelAwsMSKClusterArn";
    static final String CLUSTER_STATE = "CamelAwsMSKClusterState";

    @Override
    public String getComponent() {
        return "aws2-msk";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.msk.MSK2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(MSK_OPERATION, operation);
        }

        String clusterName = exchange.getIn().getHeader(CLUSTER_NAME, String.class);
        if (clusterName != null) {
            span.setTag(MSK_CLUSTER_NAME, clusterName);
        }

        String clusterArn = exchange.getIn().getHeader(CLUSTER_ARN, String.class);
        if (clusterArn != null) {
            span.setTag(MSK_CLUSTER_ARN, clusterArn);
        }

        String clusterState = exchange.getIn().getHeader(CLUSTER_STATE, String.class);
        if (clusterState != null) {
            span.setTag(MSK_CLUSTER_STATE, clusterState);
        }
    }

}
