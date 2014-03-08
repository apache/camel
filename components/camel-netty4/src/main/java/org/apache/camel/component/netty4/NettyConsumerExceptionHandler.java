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
package org.apache.camel.component.netty4;

import java.nio.channels.ClosedChannelException;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyConsumerExceptionHandler implements ExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NettyConsumer.class);
    private final CamelLogger logger;
    private final LoggingLevel closedLoggingLevel;

    public NettyConsumerExceptionHandler(NettyConsumer consumer) {
        this.logger = new CamelLogger(LOG, consumer.getConfiguration().getServerExceptionCaughtLogLevel());
        this.closedLoggingLevel = consumer.getConfiguration().getServerClosedChannelExceptionCaughtLogLevel();
    }

    @Override
    public void handleException(Throwable exception) {
        handleException(null, null, exception);
    }

    @Override
    public void handleException(String message, Throwable exception) {
        handleException(message, null, exception);
    }

    @Override
    public void handleException(String message, Exchange exchange, Throwable exception) {
        try {
            String msg = CamelExchangeException.createExceptionMessage(message, exchange, exception);
            boolean closed = ObjectHelper.getException(ClosedChannelException.class, exception) != null;
            if (closed) {
                logger.log(msg, exception, closedLoggingLevel);
            } else {
                logger.log(msg, exception);
            }
        } catch (Throwable e) {
            // the logging exception handler must not cause new exceptions to occur
        }
    }

}
