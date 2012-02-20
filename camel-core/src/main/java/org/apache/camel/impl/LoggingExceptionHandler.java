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
package org.apache.camel.impl;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.CamelLogger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation of {@link ExceptionHandler} which uses a {@link org.apache.camel.processor.CamelLogger} to
 * log the exception.
 * <p/>
 * This implementation will by default log the exception with stack trace at ERROR level.
 *
 * @version 
 */
public class LoggingExceptionHandler implements ExceptionHandler {
    private final CamelLogger logger;

    public LoggingExceptionHandler(Class<?> ownerType) {
        this(new CamelLogger(LoggerFactory.getLogger(ownerType), LoggingLevel.ERROR));
    }

    public LoggingExceptionHandler(Class<?> ownerType, LoggingLevel level) {
        this(new CamelLogger(LoggerFactory.getLogger(ownerType), level));
    }

    public LoggingExceptionHandler(CamelLogger logger) {
        this.logger = logger;
    }

    public void handleException(Throwable exception) {
        handleException(null, null, exception);
    }

    public void handleException(String message, Throwable exception) {
        handleException(message, null, exception);
    }

    public void handleException(String message, Exchange exchange, Throwable exception) {
        try {
            String msg = CamelExchangeException.createExceptionMessage(message, exchange, exception);
            if (isCausedByRollbackExchangeException(exception)) {
                // do not log stacktrace for intended rollbacks
                logger.log(msg);
            } else {
                if (exception != null) {
                    logger.log(msg, exception);
                } else {
                    logger.log(msg);
                }
            }
        } catch (Throwable e) {
            // the logging exception handler must not cause new exceptions to occur
        }
    }

    protected boolean isCausedByRollbackExchangeException(Throwable exception) {
        if (exception == null) {
            return false;
        }
        if (exception instanceof RollbackExchangeException) {
            return true;
        } else if (exception.getCause() != null) {
            // recursive children
            return isCausedByRollbackExchangeException(exception.getCause());
        }

        return false;
    }
}
