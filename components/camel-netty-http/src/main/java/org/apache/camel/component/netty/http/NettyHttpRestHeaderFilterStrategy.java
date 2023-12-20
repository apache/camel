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
package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;

import static org.apache.camel.support.http.HttpUtil.filterCheck;

/**
 * Default Netty {@link HeaderFilterStrategy} used when binding with {@link NettyHttpBinding}.
 */
public class NettyHttpRestHeaderFilterStrategy extends NettyHttpHeaderFilterStrategy {

    private final String templateUri;
    private final String queryParameters;

    public NettyHttpRestHeaderFilterStrategy(String templateUri, String queryParameters) {
        this.templateUri = templateUri;
        this.queryParameters = queryParameters;
    }

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        boolean answer = super.applyFilterToCamelHeaders(headerName, headerValue, exchange);
        return filterCheck(templateUri, queryParameters, headerName, answer);
    }

}
