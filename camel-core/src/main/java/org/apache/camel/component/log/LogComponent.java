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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.LoggingLevel;
import org.apache.camel.processor.ThroughputLogger;

/**
 * The <a href="http://activemq.apache.org/camel/log.html">Log Component</a>
 * to log message exchanges to the underlying logging mechanism.
 * 
 * @version $Revision$
 */
public class LogComponent extends DefaultComponent<Exchange> {

    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        LoggingLevel level = getLoggingLevel(parameters);
        Integer groupSize = getAndRemoveParameter(parameters, "groupSize", Integer.class);

        Logger logger;
        if (groupSize != null) {
            logger = new ThroughputLogger(remaining, level, ObjectConverter.toInteger(groupSize));
        } else {
            LogFormatter formatter = new LogFormatter();
            IntrospectionSupport.setProperties(formatter, parameters);

            logger = new Logger(remaining);
            logger.setLevel(level);
            logger.setFormatter(formatter);
        }

        return new ProcessorEndpoint(uri, this, logger);
    }

    /**
     * Gets the logging level, will default to use INFO if no level parameter provided.
     */
    protected LoggingLevel getLoggingLevel(Map parameters) {
        String levelText = getAndRemoveParameter(parameters, "level", String.class, "INFO");
        return LoggingLevel.valueOf(levelText.toUpperCase());
    }

}
