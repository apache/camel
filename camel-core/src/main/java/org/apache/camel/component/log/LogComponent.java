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
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.processor.CamelLogProcessor;
import org.apache.camel.processor.ThroughputLogger;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.IntrospectionSupport;

/**
 * The <a href="http://camel.apache.org/log.html">Log Component</a>
 * to log message exchanges to the underlying logging mechanism.
 *
 * @version 
 */
public class LogComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LoggingLevel level = getLoggingLevel(parameters);
        String marker = getAndRemoveParameter(parameters, "marker", String.class);
        Integer groupSize = getAndRemoveParameter(parameters, "groupSize", Integer.class);
        Long groupInterval = getAndRemoveParameter(parameters, "groupInterval", Long.class);

        CamelLogger camelLogger = new CamelLogger(remaining, level, marker);
        Processor logger;
        if (groupSize != null) {
            logger = new ThroughputLogger(camelLogger, groupSize);
        } else if (groupInterval != null) {
            Boolean groupActiveOnly = getAndRemoveParameter(parameters, "groupActiveOnly", Boolean.class, Boolean.TRUE);
            Long groupDelay = getAndRemoveParameter(parameters, "groupDelay", Long.class);
            logger = new ThroughputLogger(camelLogger, this.getCamelContext(), groupInterval, groupDelay, groupActiveOnly);
        } else {
            LogFormatter formatter = new LogFormatter();
            IntrospectionSupport.setProperties(formatter, parameters);

            logger = new CamelLogProcessor(camelLogger, formatter);
        }

        LogEndpoint endpoint = new LogEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return new LogEndpoint(uri, this, logger);
    }

    /**
     * Gets the logging level, will default to use INFO if no level parameter provided.
     */
    protected LoggingLevel getLoggingLevel(Map<String, Object> parameters) {
        String levelText = getAndRemoveParameter(parameters, "level", String.class, "INFO");
        return LoggingLevel.valueOf(levelText.toUpperCase(Locale.ENGLISH));
    }

}
