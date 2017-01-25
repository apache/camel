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
package org.apache.camel.support;

import org.apache.camel.Exchange;
import org.apache.camel.impl.cloud.ServiceCallConstants;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

public abstract class ServiceCallExpressionSupport extends ExpressionAdapter {
    private String hostHeader;
    private String portHeader;

    public ServiceCallExpressionSupport() {
        this(ServiceCallConstants.SERVICE_HOST, ServiceCallConstants.SERVICE_PORT);
    }

    public ServiceCallExpressionSupport(String hostHeader, String portHeader) {
        this.hostHeader = hostHeader;
        this.portHeader = portHeader;
    }

    public String getHostHeader() {
        return hostHeader;
    }

    public void setHostHeader(String hostHeader) {
        this.hostHeader = hostHeader;
    }

    public String getPortHeader() {
        return portHeader;
    }

    public void setPortHeader(String portHeader) {
        this.portHeader = portHeader;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            return buildCamelEndpointUri(
                ExchangeHelper.getMandatoryHeader(exchange, ServiceCallConstants.SERVICE_NAME, String.class),
                ExchangeHelper.getMandatoryHeader(exchange, hostHeader, String.class),
                exchange.getIn().getHeader(portHeader, Integer.class),
                exchange.getIn().getHeader(ServiceCallConstants.SERVICE_CALL_URI, String.class),
                exchange.getIn().getHeader(ServiceCallConstants.SERVICE_CALL_CONTEXT_PATH, String.class),
                exchange.getIn().getHeader(ServiceCallConstants.SERVICE_CALL_SCHEME, String.class));
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected abstract String buildCamelEndpointUri(String name, String host, Integer port, String uri, String contextPath, String scheme);
}