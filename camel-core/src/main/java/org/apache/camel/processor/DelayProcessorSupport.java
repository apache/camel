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
package org.apache.camel.processor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AlreadyStoppedException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A useful base class for any processor which provides some kind of throttling
 * or delayed processing
 * 
 * @version $Revision$
 */
public abstract class DelayProcessorSupport extends DelegateProcessor {
    private static final transient Log LOG = LogFactory.getLog(Delayer.class);
    private CountDownLatch stoppedLatch = new CountDownLatch(1);
    private boolean fastStop = true;

    public DelayProcessorSupport(Processor processor) {
        super(processor);
    }

    public void process(Exchange exchange) throws Exception {
        delay(exchange);
        super.process(exchange);
    }

    public boolean isFastStop() {
        return fastStop;
    }

    /**
     * Enables & disables a fast stop; basically to avoid waiting a possibly
     * long time for delays to complete before the context shuts down; instead
     * the current processing method throws
     * {@link org.apache.camel.AlreadyStoppedException} to terminate processing.
     */
    public void setFastStop(boolean fastStop) {
        this.fastStop = fastStop;
    }

    protected void doStop() throws Exception {
        stoppedLatch.countDown();
        super.doStop();
    }

    protected abstract void delay(Exchange exchange) throws Exception;

    /**
     * Wait until the given system time before continuing
     * 
     * @param time the system time to wait for
     * @param exchange the exchange being processed
     */
    protected void waitUntil(long time, Exchange exchange) throws Exception {
        while (true) {
            long delay = time - currentSystemTime();
            if (delay < 0) {
                return;
            } else {
                if (isFastStop() && !isRunAllowed()) {
                    throw new AlreadyStoppedException();
                }
                try {
                    sleep(delay);
                } catch (InterruptedException e) {
                    handleSleepInteruptedException(e);
                }
            }
        }
    }

    protected void sleep(long delay) throws InterruptedException {
        if (delay <= 0) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sleeping for: " + delay + " millis");
        }
        if (isFastStop()) {
            stoppedLatch.await(delay, TimeUnit.MILLISECONDS);
        } else {
            Thread.sleep(delay);
        }
    }

    /**
     * Called when a sleep is interupted; allows derived classes to handle this
     * case differently
     */
    protected void handleSleepInteruptedException(InterruptedException e) {
        LOG.debug("Sleep interupted: " + e, e);
    }

    protected long currentSystemTime() {
        return System.currentTimeMillis();
    }
}
