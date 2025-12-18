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

import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Processor;
import org.apache.camel.StartupListener;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnceConsumer extends DefaultConsumer implements StartupListener {

    private static final Logger LOG = LoggerFactory.getLogger(OnceConsumer.class);

    private final CamelContext camelContext;
    private final OnceEndpoint endpoint;
    private final Timer timer;
    private final TimerTask task;
    private volatile boolean scheduled;

    public OnceConsumer(OnceEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.camelContext = endpoint.getCamelContext();
        this.endpoint = endpoint;
        this.timer = new Timer(endpoint.getName());
        this.task = new OnceTimerTask();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!scheduled && endpoint.getCamelContext().getStatus().isStarted()) {
            scheduleTask(task, timer);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (task != null) {
            task.cancel();
        }
        scheduled = false;
        super.doStop();
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        if (!scheduled) {
            scheduleTask(task, timer);
        }
    }

    protected void scheduleTask(TimerTask task, Timer timer) {
        long delay = Math.max(0, endpoint.getDelay());
        LOG.debug("Scheduled once after: {} mills for task: {} ", delay, task);
        timer.schedule(task, delay);
        scheduled = true;
    }

    private class OnceTimerTask extends TimerTask {

        @Override
        public void run() {
            Exchange exchange = createExchange(false);
            try {
                // variables,headers,and body last
                if (endpoint.getVariables() != null) {
                    for (var e : endpoint.getVariables().entrySet()) {
                        Object v = resolveData(exchange, e.getValue());
                        if (v != null) {
                            exchange.setVariable(e.getKey(), v);
                        }
                    }
                }
                if (endpoint.getHeaders() != null) {
                    for (var e : endpoint.getHeaders().entrySet()) {
                        Object v = resolveData(exchange, e.getValue());
                        if (v != null) {
                            exchange.getMessage().setHeader(e.getKey(), v);
                        }
                    }
                }
                Object body = resolveData(exchange, endpoint.getBody());
                exchange.getMessage().setBody(body);
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
    }

    private Object resolveData(Exchange exchange, Object data) throws Exception {
        String answer = data instanceof String ? data.toString() : null;

        // if languages is supported then you can prefix with simple:xxx or groovy:xxx to let Camel know
        Language lan = null;
        if (answer != null && endpoint.getComponent().isLanguages() && answer.startsWith("language:")) {
            String text = answer.substring(9);
            String prefix = StringHelper.before(text, ":");
            if (prefix != null) {
                try {
                    lan = camelContext.resolveLanguage(prefix);
                    answer = StringHelper.after(text, ":");
                } catch (NoSuchLanguageException e) {
                    // ignore it's not a language
                }
            }
        }

        if (ResourceHelper.hasScheme(answer)) {
            try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, answer)) {
                answer = camelContext.getTypeConverter().mandatoryConvertTo(String.class, is);
            }
        }

        if (answer != null && lan != null) {
            return lan.createExpression(answer).evaluate(exchange, Object.class);
        }

        // data may be boolean, integer, or literal
        if ("true".equalsIgnoreCase(answer)) {
            return true;
        } else if ("false".equalsIgnoreCase(answer)) {
            return false;
        } else {
            Object val = camelContext.getTypeConverter().tryConvertTo(Integer.class, exchange, data);
            if (val != null) {
                return val;
            }
            val = camelContext.getTypeConverter().tryConvertTo(Long.class, exchange, data);
            if (val != null) {
                return val;
            }
        }

        return answer;
    }

}
