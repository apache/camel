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
package org.apache.camel.processor;

import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.CamelLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Processor} which just logs to a {@link CamelLogger} object which can be used
 * as an exception handler instead of using a dead letter queue.
 * <p/>
 * The name <tt>CamelLogger</tt> has been chosen to avoid any name clash with log kits
 * which has a <tt>Logger</tt> class.
 *
 * @version 
 */
public class CamelLogProcessor implements AsyncProcessor, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(CamelLogProcessor.class);
    private String id;
    private CamelLogger log;
    private ExchangeFormatter formatter;
    private MaskingFormatter maskingFormatter;
    private Set<LogListener> listeners;

    public CamelLogProcessor() {
        this(new CamelLogger(CamelLogProcessor.class.getName()));
    }
    
    public CamelLogProcessor(CamelLogger log) {
        this.formatter = new ToStringExchangeFormatter();
        this.log = log;
    }

    public CamelLogProcessor(CamelLogger log, ExchangeFormatter formatter, MaskingFormatter maskingFormatter, Set<LogListener> listeners) {
        this(log);
        this.formatter = formatter;
        this.maskingFormatter = maskingFormatter;
        this.listeners = listeners;
    }

    @Override
    public String toString() {
        return "Logger[" + log + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (log.shouldLog()) {
            String output = formatter.format(exchange);
            if (maskingFormatter != null) {
                output = maskingFormatter.format(output);
            }
            output = fireListeners(exchange, output);
            log.log(output);
        }
        callback.done(true);
        return true;
    }

    public void process(Exchange exchange, Throwable exception) {
        if (log.shouldLog()) {
            String output = formatter.format(exchange);
            if (maskingFormatter != null) {
                output = maskingFormatter.format(output);
            }
            output = fireListeners(exchange, output);
            log.log(output, exception);
        }
    }

    public void process(Exchange exchange, String message) {
        if (log.shouldLog()) {
            String output = formatter.format(exchange) + message;
            if (maskingFormatter != null) {
                output = maskingFormatter.format(output);
            }
            output = fireListeners(exchange, output);
            log.log(output);
        }
    }

    private String fireListeners(Exchange exchange, String message) {
        if (listeners == null) {
            return message;
        }
        for (LogListener listener : listeners) {
            if (listener == null) {
                continue;
            }
            try {
                String output = listener.onLog(exchange, log, message);
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
        return log;
    }
    
    public void setLogName(String logName) {
        log.setLogName(logName);
    }
    
    public void setLevel(LoggingLevel level) {
        log.setLevel(level);
    }

    public void setMarker(String marker) {
        log.setMarker(marker);
    }

    public void setMaskingFormatter(MaskingFormatter maskingFormatter) {
        this.maskingFormatter = maskingFormatter;
    }

    /**
     * {@link ExchangeFormatter} that calls <tt>toString</tt> on the {@link Exchange}.
     */
    static class ToStringExchangeFormatter implements ExchangeFormatter {
        public String format(Exchange exchange) {
            return exchange.toString();
        }
    }

}
