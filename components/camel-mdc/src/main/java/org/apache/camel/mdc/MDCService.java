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

import java.util.Map;

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
            LOG.warn("MDC: failed to set/unset MDC data. This exception is ignored.", t);
        }
    }

    protected void setMDC(Exchange exchange) {
        setOrUnsetMDC(exchange, true);
    }

    protected void unsetMDC(Exchange exchange) {
        setOrUnsetMDC(exchange, false);
    }

    private final class MDCLogListener implements LogListener {

        // NOTE: the onLog and afterLog are executed on the same thread, so we can
        // reliably store the context here.
        Map<String, String> previousContext;

        @Override
        public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
            previousContext = MDC.getCopyOfContextMap();
            setMDC(exchange);
            return message;
        }

        @Override
        public void afterLog(Exchange exchange, CamelLogger camelLogger, String message) {
            unsetMDC(exchange);
            MDC.setContextMap(previousContext);
        }
    }

    // Default basic MDC properties to set/unset MDC context.
    private void prepareMDC(Exchange exchange, boolean push) {
        if (push) {
            MDC.put(MDC_EXCHANGE_ID, exchange.getExchangeId());
            MDC.put(MDC_MESSAGE_ID, exchange.getMessage().getMessageId());
            MDC.put(MDC_CAMEL_CONTEXT_ID, exchange.getContext().getName());
            // Useful to make sure aync execution is properly propagating context
            MDC.put(MDC_CAMEL_THREAD_ID, Thread.currentThread().getName());
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
        } else {
            MDC.remove(MDC_EXCHANGE_ID);
            MDC.remove(MDC_MESSAGE_ID);
            MDC.remove(MDC_CAMEL_CONTEXT_ID);
            MDC.remove(MDC_CAMEL_THREAD_ID);
            String corrId = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class);
            if (corrId != null) {
                MDC.remove(MDC_CORRELATION_ID);
            }
            // Backward compatibility: this info may not be longer widely used
            String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
            if (breadcrumbId != null) {
                MDC.remove(MDC_BREADCRUMB_ID);
            }
            String routeId = exchange.getFromRouteId();
            if (routeId != null) {
                MDC.remove(MDC_ROUTE_ID);
            }
        }
    }

    // Set/unset those headers selected by the user.
    private void userSelectedHeadersMDC(Exchange exchange, boolean push) {
        for (String customHeader : getCustomHeaders().split(",")) {
            if (exchange.getIn().getHeader(customHeader) != null) {
                if (push) {
                    MDC.put(customHeader, exchange.getIn().getHeader(customHeader, String.class));
                } else {
                    MDC.remove(customHeader);
                }
            }
        }
    }

    // Set/unset all available headers.
    private void allHeadersMDC(Exchange exchange, boolean push) {
        for (String header : exchange.getIn().getHeaders().keySet()) {
            if (exchange.getIn().getHeader(header) != null) {
                if (push) {
                    MDC.put(header, exchange.getIn().getHeader(header, String.class));
                } else {
                    MDC.remove(header);
                }
            }
        }
    }

    // Set/unset those properties selected by the user.
    private void userSelectedPropertiesMDC(Exchange exchange, boolean push) {
        for (String customProperty : getCustomProperties().split(",")) {
            if (exchange.getProperty(customProperty) != null) {
                if (push) {
                    MDC.put(customProperty, exchange.getProperty(customProperty, String.class));
                } else {
                    MDC.remove(customProperty);
                }
            }
        }
    }

    // Set/unset all available properties.
    private void allPropertiesMDC(Exchange exchange, boolean push) {
        for (String property : exchange.getAllProperties().keySet()) {
            if (exchange.getProperty(property) != null) {
                if (push) {
                    MDC.put(property, exchange.getProperty(property, String.class));
                } else {
                    MDC.remove(property);
                }
            }
        }
    }

}
