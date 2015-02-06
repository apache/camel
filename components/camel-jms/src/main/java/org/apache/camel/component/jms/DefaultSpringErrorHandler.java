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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.support.LoggingExceptionHandler;
import org.springframework.util.ErrorHandler;

/**
 * A default Spring {@link ErrorHandler} that logs the exception, according to configuration options.
 */
public class DefaultSpringErrorHandler implements ErrorHandler {

    private final LoggingExceptionHandler handler;
    private final boolean logStackTrace;

    public DefaultSpringErrorHandler(CamelContext camelContext, Class<?> owner, LoggingLevel level, boolean logStackTrace) {
        this.handler = new LoggingExceptionHandler(camelContext, owner, level);
        this.logStackTrace = logStackTrace;
    }

    @Override
    public void handleError(Throwable throwable) {
        if (logStackTrace) {
            handler.handleException("Execution of JMS message listener failed", throwable);
        } else {
            handler.handleException("Execution of JMS message listener failed. Caused by: [" + throwable.getMessage() + "]", null);
        }
    }

}
