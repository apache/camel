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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThroughputLoggerMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.ThroughputLogger;

/**
 *
 */
@ManagedResource(description = "Managed ThroughputLogger")
public class ManagedThroughputLogger extends ManagedProcessor implements ManagedThroughputLoggerMBean {

    private final ThroughputLogger logger;

    public ManagedThroughputLogger(CamelContext context, ThroughputLogger logger, ProcessorDefinition<?> definition) {
        super(context, logger, definition);
        this.logger = logger;
    }

    public ThroughputLogger getLogger() {
        return logger;
    }

    @Override
    public void reset() {
        super.reset();
        logger.reset();
    }

    public int getReceivedCounter() {
        return logger.getReceivedCounter();
    }

    public double getAverage() {
        return logger.getAverage();
    }

    public double getRate() {
        return logger.getRate();
    }

    public String getLastLogMessage() {
        return logger.getLastLogMessage();
    }

    public void resetThroughputLogger() {
        logger.reset();
    }
}
