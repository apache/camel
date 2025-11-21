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
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@JdkService("mdc-service")
public class MDCService extends ServiceSupport implements CamelMDCService {

    static String MDC_BREADCRUMB_ID = "camel.breadcrumbId";
    static String MDC_EXCHANGE_ID = "camel.exchangeId";
    static String MDC_MESSAGE_ID = "camel.messageId";
    static String MDC_CORRELATION_ID = "camel.correlationId";
    static String MDC_ROUTE_ID = "camel.routeId";
    static String MDC_CAMEL_THREAD_ID = "camel.threadId";
    static String MDC_CAMEL_CONTEXT_ID = "camel.contextId";

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
        camelContext.getCamelContextExtension().addLogListener(new MDCLogListener());
        InterceptStrategy interceptStrategy = new MDCProcessorsInterceptStrategy(this);
        camelContext.getCamelContextExtension().addInterceptStrategy(interceptStrategy);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Mapped Diagnostic Context (MDC) enabled");
    }

    private void setOrUnsetMDC(Exchange exchange, boolean push) {
        try {
            // Default values
            prepareMDC(exchange, push);
            if (getCustomHeaders() != null) {
                if (getCustomHeaders().equals("*")) {
                    allHeadersMDC(exchange, push);
                } else {
                    userSelectedHeadersMDC(exchange, push);
                }
            }
            if (getCustomProperties() != null) {
                if (getCustomProperties().equals("*")) {
                    allPropertiesMDC(exchange, push);
                } else {
                    userSelectedPropertiesMDC(exchange, push);
                }
            }
        } catch (Exception t) {
            // This exception is ignored
            LOG.warn("MDC: failed to store MDC data. This exception is ignored.", t);
        }
    }

    protected void setMDC(Exchange exchange) {
        setOrUnsetMDC(exchange, true);
    }

    protected void unsetMDC(Exchange exchange) {
        setOrUnsetMDC(exchange, false);
    }

    private final class MDCLogListener implements LogListener {

        @Override
        public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
            setMDC(exchange);
            return message;
        }

        @Override
        public void afterLog(Exchange exchange, CamelLogger camelLogger, String message) {
            unsetMDC(exchange);
        }
    }

    // Default basic MDC properties to set/unset MDC context. It leverage the stack capabilities of the MDC.
    private void prepareMDC(Exchange exchange, boolean push) {
        if (push) {
            MDC.pushByKey(MDC_EXCHANGE_ID, exchange.getExchangeId());
            MDC.pushByKey(MDC_MESSAGE_ID, exchange.getMessage().getMessageId());
            MDC.pushByKey(MDC_CAMEL_CONTEXT_ID, exchange.getContext().getName());
            // Useful to make sure aync execution is properly propagating context
            MDC.pushByKey(MDC_CAMEL_THREAD_ID, Thread.currentThread().getName());
            // Backward compatibility: this info may not be longer widely used
            String corrId = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class);
            if (corrId != null) {
                MDC.pushByKey(MDC_CORRELATION_ID, corrId);
            }
            // Backward compatibility: this info may not be longer widely used
            String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
            if (breadcrumbId != null) {
                MDC.pushByKey(MDC_BREADCRUMB_ID, breadcrumbId);
            }
            String routeId = exchange.getFromRouteId();
            if (routeId != null) {
                MDC.pushByKey(MDC_ROUTE_ID, routeId);
            }
        } else {
            popAndClear(MDC_EXCHANGE_ID);
            popAndClear(MDC_MESSAGE_ID);
            popAndClear(MDC_CAMEL_CONTEXT_ID);
            popAndClear(MDC_CAMEL_THREAD_ID);
            popAndClear(MDC_CORRELATION_ID);
            popAndClear(MDC_BREADCRUMB_ID);
            popAndClear(MDC_ROUTE_ID);
        }
    }

    // popAndClear pop the value and clear the key if nothing exists.
    private void popAndClear(String key) {
        MDC.popByKey(key);
        if (MDC.get(key) == null) {
            MDC.remove(key);
        }
    }

    // Set/unset those headers selected by the user. It leverage the stack capabilities of the MDC.
    private void userSelectedHeadersMDC(Exchange exchange, boolean push) {
        for (String customHeader : getCustomHeaders().split(",")) {
            if (exchange.getIn().getHeader(customHeader) != null) {
                if (push) {
                    MDC.pushByKey(customHeader, exchange.getIn().getHeader(customHeader, String.class));
                } else {
                    popAndClear(customHeader);
                }
            }
        }
    }

    // Set/unset all available headers. It leverage the stack capabilities of the MDC.
    private void allHeadersMDC(Exchange exchange, boolean push) {
        for (String header : exchange.getIn().getHeaders().keySet()) {
            if (exchange.getIn().getHeader(header) != null) {
                if (push) {
                    MDC.pushByKey(header, exchange.getIn().getHeader(header, String.class));
                } else {
                    popAndClear(header);
                }
            }
        }
    }

    // Set/unset those properties selected by the user. It leverage the stack capabilities of the MDC.
    private void userSelectedPropertiesMDC(Exchange exchange, boolean push) {
        for (String customProperty : getCustomProperties().split(",")) {
            if (exchange.getProperty(customProperty) != null) {
                if (push) {
                    MDC.pushByKey(customProperty, exchange.getProperty(customProperty, String.class));
                } else {
                    popAndClear(customProperty);
                }
            }
        }
    }

    // Set/unset all available properties. It leverage the stack capabilities of the MDC.
    private void allPropertiesMDC(Exchange exchange, boolean push) {
        for (String property : exchange.getAllProperties().keySet()) {
            if (exchange.getProperty(property) != null) {
                if (push) {
                    MDC.pushByKey(property, exchange.getProperty(property, String.class));
                } else {
                    popAndClear(property);
                }
            }
        }
    }

}
