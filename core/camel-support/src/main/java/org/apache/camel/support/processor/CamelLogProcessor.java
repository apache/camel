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
package org.apache.camel.support.processor;

import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Processor} which just logs to a {@link CamelLogger} object which can be used
 * as an exception handler instead of using a dead letter queue.
 * <p/>
 * The name <tt>CamelLogger</tt> has been chosen to avoid any name clash with log kits
 * which has a <tt>Logger</tt> class.
 */
public class CamelLogProcessor extends AsyncProcessorSupport implements IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(CamelLogProcessor.class);

    private String id;
    private String routeId;
    private CamelLogger logger;
    private ExchangeFormatter formatter;
    private MaskingFormatter maskingFormatter;
    private final Set<LogListener> listeners;

    public CamelLogProcessor() {
        this(new CamelLogger(CamelLogProcessor.class.getName()));
    }
    
    public CamelLogProcessor(CamelLogger logger) {
        this.formatter = new ToStringExchangeFormatter();
        this.logger = logger;
        this.listeners = null;
    }

    public CamelLogProcessor(CamelLogger logger, ExchangeFormatter formatter, MaskingFormatter maskingFormatter, Set<LogListener> listeners) {
        this.formatter = new ToStringExchangeFormatter();
        this.logger = logger;
        this.formatter = formatter;
        this.maskingFormatter = maskingFormatter;
        this.listeners = listeners;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (logger.shouldLog()) {
            String output = formatter.format(exchange);
            if (maskingFormatter != null) {
                output = maskingFormatter.format(output);
            }
            output = fireListeners(exchange, output);
            logger.log(output);
        }
        callback.done(true);
        return true;
    }

    public void process(Exchange exchange, Throwable exception) {
        if (logger.shouldLog()) {
            String output = formatter.format(exchange);
            if (maskingFormatter != null) {
                output = maskingFormatter.format(output);
            }
            output = fireListeners(exchange, output);
            logger.log(output, exception);
        }
    }

    public void process(Exchange exchange, String message) {
        if (logger.shouldLog()) {
            String output = formatter.format(exchange) + message;
            if (maskingFormatter != null) {
                output = maskingFormatter.format(output);
            }
            output = fireListeners(exchange, output);
            logger.log(output);
        }
    }

    private String fireListeners(Exchange exchange, String message) {
        if (listeners == null || listeners.isEmpty()) {
            return message;
        }
        for (LogListener listener : listeners) {
            if (listener == null) {
                continue;
            }
            try {
                String output = listener.onLog(exchange, logger, message);
                message = output != null ? output : message;
            } catch (Throwable t) {
                LOG.warn("Ignoring an exception thrown by {}: {}", listener.getClass().getName(), t.getMessage());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("", t);
                }
            }
        }
        return message;
    }

    public CamelLogger getLogger() {
        return logger;
    }
    
    public void setLogName(String logName) {
        logger.setLogName(logName);
    }
    
    public void setLevel(LoggingLevel level) {
        logger.setLevel(level);
    }

    public void setMarker(String marker) {
        logger.setMarker(marker);
    }

    public void setMaskingFormatter(MaskingFormatter maskingFormatter) {
        this.maskingFormatter = maskingFormatter;
    }

    /**
     * {@link ExchangeFormatter} that calls <tt>toString</tt> on the {@link Exchange}.
     */
    static class ToStringExchangeFormatter implements ExchangeFormatter {
        @Override
        public String format(Exchange exchange) {
            return exchange.toString();
        }
    }

}
