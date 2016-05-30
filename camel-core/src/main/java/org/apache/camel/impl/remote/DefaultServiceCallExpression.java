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

package org.apache.camel.impl.remote;

import org.apache.camel.Exchange;
import org.apache.camel.support.ServiceCallExpressionSupport;
import org.apache.camel.util.ExchangeHelper;

public class DefaultServiceCallExpression extends ServiceCallExpressionSupport {
    private final String ipHeader;
    private final String portHeader;

    public DefaultServiceCallExpression(String name, String scheme, String contextPath, String uri) {
        this(name, scheme, contextPath, uri, ServiceCallConstants.SERVER_IP, ServiceCallConstants.SERVER_PORT);
    }

    public DefaultServiceCallExpression(String name, String scheme, String contextPath, String uri, String ipHeader, String portHeader) {
        super(name, scheme, contextPath, uri);

        this.ipHeader = ipHeader;
        this.portHeader = portHeader;
    }

    @Override
    public String getIp(Exchange exchange) throws Exception {
        return ExchangeHelper.getMandatoryHeader(exchange, ipHeader, String.class);
    }

    @Override
    public int getPort(Exchange exchange) throws Exception {
        return ExchangeHelper.getMandatoryHeader(exchange, portHeader, int.class);
    }
}