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
package org.apache.camel.component.timer;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The timer consumer.
 *
 * @version $Revision$
 */
public class TimerConsumer extends DefaultConsumer<Exchange> {
    private static final transient Log LOG = LogFactory.getLog(TimerConsumer.class);
    private final TimerEndpoint endpoint;
    private TimerTask task;

    public TimerConsumer(TimerEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        task = new TimerTask() {
            @Override
            public void run() {
                sendTimerExchange();
            }
        };

        Timer timer = endpoint.getTimer();
        configureTask(task, timer);
    }

    @Override
    protected void doStop() throws Exception {
        task.cancel();
    }

    protected void configureTask(TimerTask task, Timer timer) {
        if (endpoint.isFixedRate()) {
            if (endpoint.getTime() != null) {
                timer.scheduleAtFixedRate(task, endpoint.getTime(), endpoint.getPeriod());
            } else {
                timer.scheduleAtFixedRate(task, endpoint.getDelay(), endpoint.getPeriod());
            }
        } else {
            if (endpoint.getTime() != null) {
                if (endpoint.getPeriod() >= 0) {
                    timer.schedule(task, endpoint.getTime(), endpoint.getPeriod());
                } else {
                    timer.schedule(task, endpoint.getTime());
                }
            } else {
                if (endpoint.getPeriod() >= 0) {
                    timer.schedule(task, endpoint.getDelay(), endpoint.getPeriod());
                } else {
                    timer.schedule(task, endpoint.getDelay());
                }
            }
        }
    }

    protected void sendTimerExchange() {
        Exchange exchange = endpoint.createExchange();
        exchange.setProperty("org.apache.camel.timer.name", endpoint.getTimerName());
        exchange.setProperty("org.apache.camel.timer.time", endpoint.getTime());
        exchange.setProperty("org.apache.camel.timer.period", endpoint.getPeriod());

        Date now = new Date();
        exchange.setProperty("org.apache.camel.timer.firedTime", now);
        // also set now on in header with same key as quaartz to be consistent
        exchange.getIn().setHeader("firedTime", now);

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOG.error("Caught: " + e, e);
        }
    }
}
