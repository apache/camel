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
package org.apache.camel.component.log;

import java.util.Locale;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.processor.CamelLogProcessor;
import org.apache.camel.processor.DefaultExchangeFormatter;
import org.apache.camel.processor.ThroughputLogger;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.util.CamelLogger;

/**
 * The <a href="http://camel.apache.org/log.html">Log Component</a>
 * to log message exchanges to the underlying logging mechanism.
 *
 * @version 
 */
public class LogComponent extends UriEndpointComponent {

    private ExchangeFormatter exchangeFormatter;

    public LogComponent() {
        super(LogEndpoint.class);
    }
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LoggingLevel level = getLoggingLevel(parameters);

        LogEndpoint endpoint = new LogEndpoint(uri, this);
        endpoint.setLevel(level.name());
        setProperties(endpoint, parameters);

        CamelLogger camelLogger = new CamelLogger(remaining, level, endpoint.getMarker());
        Processor logger;
        if (endpoint.getGroupSize() != null) {
            logger = new ThroughputLogger(camelLogger, endpoint.getGroupSize());
        } else if (endpoint.getGroupInterval() != null) {
            Boolean groupActiveOnly = endpoint.getGroupActiveOnly() != null ? endpoint.getGroupActiveOnly() : Boolean.TRUE;
            Long groupDelay = endpoint.getGroupDelay();
            logger = new ThroughputLogger(camelLogger, this.getCamelContext(), endpoint.getGroupInterval(), groupDelay, groupActiveOnly);
        } else {
            // first, try to use the user-specified formatter (or the one picked up from the Registry and transferred to 
            // the property by a previous endpoint initialisation); if null, try to pick it up from the Registry now
            ExchangeFormatter localFormatter = exchangeFormatter;
            if (localFormatter == null) {
                localFormatter = getCamelContext().getRegistry().lookupByNameAndType("logFormatter", ExchangeFormatter.class);
                if (localFormatter != null) {
                    exchangeFormatter = localFormatter;
                    setProperties(exchangeFormatter, parameters);
                }
            }
            // if no formatter is available in the Registry, create a local one of the default type, for a single use
            if (localFormatter == null) {
                localFormatter = new DefaultExchangeFormatter();
                setProperties(localFormatter, parameters);
            }
            logger = new CamelLogProcessor(camelLogger, localFormatter);
        }

        endpoint.setLogger(logger);
        return endpoint;
    }

    /**
     * Gets the logging level, will default to use INFO if no level parameter provided.
     */
    protected LoggingLevel getLoggingLevel(Map<String, Object> parameters) {
        String levelText = getAndRemoveParameter(parameters, "level", String.class, "INFO");
        return LoggingLevel.valueOf(levelText.toUpperCase(Locale.ENGLISH));
    }

    public ExchangeFormatter getExchangeFormatter() {
        return exchangeFormatter;
    }

    /**
     * Sets a custom {@link ExchangeFormatter} to convert the Exchange to a String suitable for logging.
     * <p />
     * If not specified, we default to {@link DefaultExchangeFormatter}.
     */
    public void setExchangeFormatter(ExchangeFormatter exchangeFormatter) {
        this.exchangeFormatter = exchangeFormatter;
    }

}
