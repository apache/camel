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
package org.apache.camel.spi;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.CamelLogProcessor;
import org.apache.camel.processor.LogProcessor;
import org.apache.camel.util.CamelLogger;
import org.slf4j.Marker;

/**
 * An event listener SPI for logging. Listeners are registered into {@link LogProcessor} and
 * {@link CamelLogProcessor} so that the logging events are delivered for both of Log Component and Log EIP.
 * 
 */
public interface LogListener {

    /**
     * Invoked right before Log component or Log EIP logs.
     * Note that {@link CamelLogger} holds the {@link LoggingLevel} and {@link Marker}.
     * The listener can check {@link CamelLogger#getLevel()} to see in which log level
     * this is going to be logged.
     * 
     * @param exchange camel exchange
     * @param camelLogger {@link CamelLogger}
     * @param message log message
     * @return log message, possibly enriched by the listener
     */
    String onLog(Exchange exchange, CamelLogger camelLogger, String message);

}
