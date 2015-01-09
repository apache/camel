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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.processor.DefaultExchangeFormatter;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimeoutInflightRepository just checks the inflight exchanges and call the processTimeoutExchange() if the exchange processing is timeout.
 * It could be useful if we want to find out which exchange is processed for a long time. 
 * Please use CamelContext.startService(repository) to start the service before set it to the CamelContext; 
 */
public class TimeoutInflightRepository extends ServiceSupport implements InflightRepository {

    // TODO: rework this a bit and likely add support for this to the default inflight repository

    private static final Logger LOG = LoggerFactory.getLogger(TimeoutInflightRepository.class);
    private static final String INFLIGHT_TIME_STAMP = "CamelInflightTimeStamp";
    private static final String TIMEOUT_EXCHANGE_PROCESSED = "CamelTimeoutExchangeProcessed";
    private ExchangeFormatter exchangeFormatter;
    private final Map<String, Exchange> inflightExchanges = new ConcurrentHashMap<String, Exchange>();
    private long waitTime = 60 * 1000;
    private long timeout = 60 * 1000;

    private InspectorWorker woker;
    private Thread exchangeWatchDog;
    
    @Override
    protected void doStart() throws Exception {
        if (exchangeFormatter == null) {
            // setup exchange formatter to be used for message history dump
            DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();
            formatter.setShowExchangeId(true);
            formatter.setShowProperties(true);
            formatter.setMultiline(true);
            formatter.setShowHeaders(true);
            formatter.setStyle(DefaultExchangeFormatter.OutputStyle.Fixed);
            this.exchangeFormatter = formatter;
        }
    
        if (exchangeWatchDog == null) {
            woker = new InspectorWorker(waitTime, timeout);
            exchangeWatchDog = new Thread(woker);
        }
        exchangeWatchDog.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (woker != null) {
            woker.stop();
            exchangeWatchDog = null;
        }
    }

    @Override
    public void add(Exchange exchange) {
        exchange.setProperty(INFLIGHT_TIME_STAMP, new Long(System.currentTimeMillis()));
        // setup the time stamp of the exchange
        inflightExchanges.put(exchange.getExchangeId(), exchange);
    }

    @Override
    public void remove(Exchange exchange) {
        exchange.removeProperty(INFLIGHT_TIME_STAMP);
        inflightExchanges.remove(exchange.getExchangeId());
    }

    @Override
    public void add(Exchange exchange, String routeId) {
        // do nothing here
    }

    @Override
    public void remove(Exchange exchange, String routeId) {
        // do nothing here
    }

    @Override
    public int size() {
        return inflightExchanges.size();
    }

    @Override
    public int size(Endpoint endpoint) {
        // do nothing here
        return 0;
    }

    @Override
    public void removeRoute(String routeId) {
        // We don't support this interface yet
    }

    @Override
    public int size(String routeId) {
        // do nothing here
        return 0;
    }

    @Override
    public Collection<InflightExchange> browse() {
        return null;
    }

    @Override
    public Collection<InflightExchange> browse(int limit, boolean sortByLongestDuration) {
        return null;
    }

    public long getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    
    public ExchangeFormatter getExchangeFormatter() {
        return exchangeFormatter;
    }

    public void setExchangeFormatter(ExchangeFormatter exchangeFormatter) {
        this.exchangeFormatter = exchangeFormatter;
    }

    protected void processTimeoutExchange(Exchange exchange, long processingTime) {
        // print out exchange history or send an alarm
        // dump a route stack trace of the exchange
        StringBuilder sb = new StringBuilder();
        sb.append("Got the inflight exchange which is stay in the repostory for about ").append(processingTime).append(" With exchangeID: ");
        sb.append(exchange.getExchangeId());
        sb.append("\n");
        String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, exchangeFormatter, false);
        if (routeStackTrace != null) {
            sb.append(routeStackTrace);
        }
        LOG.error(sb.toString());
    }
    
    // Just find out the exchange which is inflight repository for a very long time
    class InspectorWorker implements Runnable {
        private final long timeout;
        private final long waitTime;
        private boolean stop;
        
        InspectorWorker(long timeout, long waitTime) {
            this.timeout = timeout;
            this.waitTime = waitTime;
        }
        
        public void stop() {
            stop = true;
        }

        @Override
        public void run() {
            while (!stop) {
                for (Exchange exchange : inflightExchanges.values()) {
                    // check if the exchange is timeout
                    long timeStamp = exchange.getProperty(INFLIGHT_TIME_STAMP, Long.class);
                    Boolean processed = exchange.getProperty(TIMEOUT_EXCHANGE_PROCESSED, Boolean.FALSE, Boolean.class);
                    long processingTime = System.currentTimeMillis() - timeStamp;
                    if (!processed && processingTime > timeout) {
                        processTimeoutExchange(exchange, processingTime);
                        exchange.setProperty(TIMEOUT_EXCHANGE_PROCESSED, Boolean.TRUE);
                    }
                }
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    // do nothing here, we just use stop flag to stop the worker
                }
            }
            
        }
        
    }

}
