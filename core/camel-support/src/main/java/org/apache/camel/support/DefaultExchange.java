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
package org.apache.camel.support;

import java.util.EnumMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;

/**
 * The default and only implementation of {@link Exchange}.
 */
public final class DefaultExchange extends AbstractExchange {

    DefaultExchange(CamelContext context, EnumMap<ExchangePropertyKey, Object> internalProperties,
                    Map<String, Object> properties) {
        super(context, internalProperties, properties);
    }

    public DefaultExchange(CamelContext context) {
        super(context);
    }

    public DefaultExchange(CamelContext context, ExchangePattern pattern) {
        super(context, pattern);
    }

    public DefaultExchange(Exchange parent) {
        super(parent);
    }

    public DefaultExchange(Endpoint fromEndpoint) {
        super(fromEndpoint);
    }

    public DefaultExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        super(fromEndpoint, pattern);
    }
}
