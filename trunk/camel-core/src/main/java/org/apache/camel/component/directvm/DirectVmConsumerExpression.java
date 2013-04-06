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
package org.apache.camel.component.directvm;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.AntPathMatcher;

/**
 * The expression to select direct-vm consumers based on ant-like path pattern matching.
 */
public class DirectVmConsumerExpression extends ExpressionAdapter {

    private final AntPathMatcher matcher;
    private final String pattern;

    public DirectVmConsumerExpression(String pattern) {
        this.matcher = new AntPathMatcher();
        this.pattern = pattern;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        Collection<Endpoint> endpoints = new ArrayList<Endpoint>();
        for (Endpoint endpoint : DirectVmComponent.getConsumerEndpoints()) {
            if (matcher.match(pattern, endpoint.getEndpointKey())) {
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    @Override
    public String toString() {
        return "DirectVmConsumerExpression[" + pattern + "]";
    }
}
