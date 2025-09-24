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
package org.apache.camel.mdc;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.CamelMDCService;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@JdkService("mdc-service")
public class MDCService extends ServiceSupport implements CamelMDCService {

    private String MDC_BREADCRUMB_ID = "camel.breadcrumbId";
    private String MDC_EXCHANGE_ID = "camel.exchangeId";
    private String MDC_MESSAGE_ID = "camel.messageId";
    private String MDC_CORRELATION_ID = "camel.correlationId";
    private String MDC_ROUTE_ID = "camel.routeId";
    private String MDC_CAMEL_CONTEXT_ID = "camel.contextId";

    private static final Logger LOG = LoggerFactory.getLogger(MDCService.class);

    private CamelContext camelContext;

    private String customHeaders;
    private String customProperties;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(String customHeaders) {
        this.customHeaders = customHeaders;
    }

    public String getCustomProperties() {
        return customProperties;
    }

    public void setCustomProperties(String customProperties) {
        this.customProperties = customProperties;
    }

    /**
     * Registers this {@link MDCService} on the {@link CamelContext} if not already registered.
     */
    public void init(CamelContext camelContext) {
        if (!camelContext.hasService(this)) {
            try {
                // start this service eager so we init before Camel is starting up
                camelContext.addService(this, true, true);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public void doInit() {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        camelContext.getCamelContextExtension().addLogListener(new TracingLogListener());
    }

    private final class TracingLogListener implements LogListener {

        @Override
        public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
            try {
                // Default values
                prepareMDC(exchange);
                if (getCustomHeaders() != null) {
                    // User headers values
                    userSelectedHeadersMDC(exchange);
                }
                if (getCustomProperties() != null) {
                    // User headers values
                    userSelectedPropertiesMDC(exchange);
                }
            } catch (Exception t) {
                // This exception is ignored
                LOG.warn("MDC: failed to store MDC data. This exception is ignored.", t);
            }
            return message;
        }

        @Override
        public void afterLog(Exchange exchange, CamelLogger camelLogger, String message) {
            MDC.clear();
        }

        // Default basic MDC properties to set
        private void prepareMDC(Exchange exchange) {
            MDC.put(MDC_EXCHANGE_ID, exchange.getExchangeId());
            MDC.put(MDC_MESSAGE_ID, exchange.getMessage().getMessageId());
            MDC.put(MDC_CAMEL_CONTEXT_ID, exchange.getContext().getName());
            // Backward compatibility: this info may not be longer widely used
            String corrId = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class);
            if (corrId != null) {
                MDC.put(MDC_CORRELATION_ID, corrId);
            }
            // Backward compatibility: this info may not be longer widely used
            String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
            if (breadcrumbId != null) {
                MDC.put(MDC_BREADCRUMB_ID, breadcrumbId);
            }
            String routeId = exchange.getFromRouteId();
            if (routeId != null) {
                MDC.put(MDC_ROUTE_ID, routeId);
            }
        }

        // Include those headers selected by the user
        private void userSelectedHeadersMDC(Exchange exchange) {
            for (String customHeader : getCustomHeaders().split(",")) {
                if (exchange.getIn().getHeader(customHeader) != null) {
                    MDC.put(customHeader, exchange.getIn().getHeader(customHeader, String.class));
                }
            }
        }

        // Include those properties selected by the user
        private void userSelectedPropertiesMDC(Exchange exchange) {
            for (String customProperty : getCustomProperties().split(",")) {
                if (exchange.getProperty(customProperty) != null) {
                    MDC.put(customProperty, exchange.getProperty(customProperty, String.class));
                }
            }
        }
    }

}
