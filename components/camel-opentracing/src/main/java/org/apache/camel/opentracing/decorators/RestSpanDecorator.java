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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.opentracing.Span;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class RestSpanDecorator extends AbstractHttpSpanDecorator {

    @Override
    public String getComponent() {
        return "rest";
    }

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        String path = exchange.getFromEndpoint().getEndpointUri();
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }
        return path;
    }

    @Override
    public void pre(Span span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);

        getParameters(exchange.getFromEndpoint().getEndpointUri()).forEach(param -> {
            Object value = exchange.getIn().getHeader(param);
            if (value != null) {
                if (value instanceof String) {
                    span.setTag(param, (String)value);
                } else if (value instanceof Number) {
                    span.setTag(param, (Number)value);
                } else if (value instanceof Boolean) {
                    span.setTag(param, (Boolean)value);
                }
            }
        });
        
    }

    public static List<String> getParameters(String uri) {
        List<String> parameters = null;

        int startIndex = uri.indexOf('(');
        while (startIndex != -1) {
            int endIndex = uri.indexOf(')', startIndex);
            if (endIndex != -1) {
                if (parameters == null) {
                    parameters = new ArrayList<>();
                }
                parameters.add(uri.substring(startIndex + 1, endIndex));
                startIndex = uri.indexOf('(', endIndex);
            } else {
                // Break out of loop as no valid end token
                startIndex = -1;
            }
        }

        return parameters == null ? Collections.emptyList() : parameters;
    }
}
