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
package org.apache.camel.component.once;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StartupListener;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnceConsumer extends DefaultConsumer implements StartupListener {

    private static final Logger LOG = LoggerFactory.getLogger(OnceConsumer.class);

    private final OnceEndpoint endpoint;
    private final Timer timer;
    private TimerTask task;
    private volatile boolean scheduled;

    public OnceConsumer(OnceEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.timer = new Timer(endpoint.getName());
    }

    @Override
    public void doInit() throws Exception {
        task = new TimerTask() {
            @Override
            public void run() {
                Exchange exchange = createExchange(false);
                try {
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }

                // handle any thrown exception
                try {
                    if (exchange.getException() != null) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                    }
                } finally {
                    releaseExchange(exchange, false);
                }
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (task != null && !scheduled && endpoint.getCamelContext().getStatus().isStarted()) {
            scheduleTask(task, timer);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (task != null) {
            task.cancel();
        }
        task = null;
        scheduled = false;

        super.doStop();
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        if (task != null && !scheduled) {
            scheduleTask(task, timer);
        }
    }

    protected void scheduleTask(TimerTask task, Timer timer) {
        LOG.debug("Scheduled once after: {} mills for task: {} ", endpoint.getDelay(), task);
        timer.schedule(task, endpoint.getDelay());
        scheduled = true;
    }
}
