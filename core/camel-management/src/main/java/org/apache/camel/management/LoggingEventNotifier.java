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
package org.apache.camel.management;

import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging event notifier that only notifies if <tt>INFO</tt> log level has
 * been configured for its logger.
 */
public class LoggingEventNotifier extends EventNotifierSupport {

    private Logger log = LoggerFactory.getLogger(LoggingEventNotifier.class);
    private String logName;

    @Override
    public void notify(CamelEvent event) throws Exception {
        log.info("Event: {}", event);
    }

    @Override
    public boolean isDisabled() {
        return !log.isInfoEnabled();
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return log.isInfoEnabled();
    }

    public String getLogName() {
        return logName;
    }

    /**
     * Sets the log name to use.
     *
     * @param logName a custom log name to use
     */
    public void setLogName(String logName) {
        this.logName = logName;
    }

    @Override
    protected void doStart() throws Exception {
        if (logName != null) {
            log = LoggerFactory.getLogger(logName);
        }
    }

}
