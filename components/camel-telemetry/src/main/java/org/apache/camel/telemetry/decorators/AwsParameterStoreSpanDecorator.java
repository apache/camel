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

public class AwsParameterStoreSpanDecorator extends AbstractSpanDecorator {

    static final String PARAMETER_STORE_OPERATION = "operation";
    static final String PARAMETER_STORE_NAME = "parameterName";
    static final String PARAMETER_STORE_PATH = "parameterPath";

    /**
     * Constants copied from {@link org.apache.camel.component.aws.parameterstore.ParameterStoreConstants}
     */
    static final String OPERATION = "CamelAwsParameterStoreOperation";
    static final String PARAMETER_NAME = "CamelAwsParameterStoreName";
    static final String PARAMETER_PATH = "CamelAwsParameterStorePath";

    @Override
    public String getComponent() {
        return "aws-parameter-store";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws.parameterstore.ParameterStoreComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(PARAMETER_STORE_OPERATION, operation);
        }

        String parameterName = exchange.getIn().getHeader(PARAMETER_NAME, String.class);
        if (parameterName != null) {
            span.setTag(PARAMETER_STORE_NAME, parameterName);
        }

        String parameterPath = exchange.getIn().getHeader(PARAMETER_PATH, String.class);
        if (parameterPath != null) {
            span.setTag(PARAMETER_STORE_PATH, parameterPath);
        }
    }

}
