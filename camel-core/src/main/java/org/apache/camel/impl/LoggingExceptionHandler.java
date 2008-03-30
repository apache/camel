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

import org.apache.camel.processor.Logger;
import org.apache.camel.processor.LoggingLevel;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.commons.logging.LogFactory;

/**
 * A default implementation of {@link ExceptionHandler} which uses a {@link Logger} to
 * log to an arbitrary {@link org.apache.commons.logging.Log Log} with some {@link LoggingLevel}
 *
 * @version $Revision$
 */
public class LoggingExceptionHandler implements ExceptionHandler {
    private final Logger logger;

    public LoggingExceptionHandler(Class ownerType) {
        this(new Logger(LogFactory.getLog(ownerType), LoggingLevel.ERROR));
    }

    public LoggingExceptionHandler(Logger logger) {
        this.logger = logger;
    }

    public void handleException(Throwable exception) {
        logger.log(exception.getMessage(), exception);
    }
}
