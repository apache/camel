/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import java.util.concurrent.atomic.AtomicInteger;
import java.text.MessageFormat;
import java.text.NumberFormat;

import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;

/**
 * @version $Revision: 1.1 $
 */
public class ThroughputLogger extends Logger {
    private int groupSize = 100;
    private long startTime;
    private AtomicInteger receivedCounter = new AtomicInteger();
    private NumberFormat numberFormat = NumberFormat.getNumberInstance();

    public ThroughputLogger() {
    }

    public ThroughputLogger(Log log) {
        super(log);
    }

    public ThroughputLogger(Log log, LoggingLevel level) {
        super(log, level);
    }

    public ThroughputLogger(String logName) {
        super(logName);
    }

    public ThroughputLogger(String logName, LoggingLevel level) {
        super(logName, level);
    }

    public ThroughputLogger(String logName, LoggingLevel level, int groupSize) {
        super(logName, level);
        setGroupSize(groupSize);
    }

    public ThroughputLogger(String logName, int groupSize) {
        super(logName);
        setGroupSize(groupSize);
    }

    public ThroughputLogger(int groupSize) {
        setGroupSize(groupSize);
    }

    @Override
    public void process(Exchange exchange) {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        int receivedCount = receivedCounter.incrementAndGet();
        if (receivedCount % groupSize == 0) {
            super.process(exchange);
        }
    }

    public int getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(int groupSize) {
        if (groupSize == 0) {
            throw new IllegalArgumentException("groupSize cannot be zero!");
        }
        this.groupSize = groupSize;
    }

    public NumberFormat getNumberFormat() {
        return numberFormat;
    }

    public void setNumberFormat(NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    @Override
    protected Object logMessage(Exchange exchange) {
        long time = System.currentTimeMillis();
        long elapsed = time - startTime;
        startTime = time;

        // timeOneMessage = time / group
        // messagePerSend = 1000 / timeOneMessage
        double rate = groupSize * 1000.0;
        rate /= elapsed;

        return "Received: " + receivedCounter.get() + " messages so far. Last group took: " + elapsed + " millis which is: " + numberFormat.format(rate) + " messages per second";
    }
}
