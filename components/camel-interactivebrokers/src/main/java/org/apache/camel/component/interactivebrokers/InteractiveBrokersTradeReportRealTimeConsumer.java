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
package org.apache.camel.component.interactivebrokers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.Execution;
import com.ib.client.ExecutionFilter;
import com.ib.controller.ApiController.ITradeReportHandler;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls the ApiController.reqExecutions() method to detect new trades
 * and generates an Exchange for each trade.
 * 
 * Unfortunately, the reqExecutions() method does not provide a real-time
 * feed of trades.  Each time it is called, it returns all matching trades,
 * including those that may have been returned on previous invocations.
 * 
 * Therefore, we use a cache to remember which tradeKey values have
 * already been seen and to ensure the same tradeKey is not sent to
 * the route twice.  Beware, the cache doesn't survive a restart of the process
 * and so your application logic should also be sensitive to duplicate trade
 * reports.
 *
 */
public class InteractiveBrokersTradeReportRealTimeConsumer
        extends InteractiveBrokersConsumer
        implements ITradeReportHandler  {

    public static final String TYPE_NAME = "tradeReport";
    // Interval between polls (milliseconds)
    public static final int DEFAULT_POLL_FREQUENCY = 5000;
    // How long to track trade IDs we saw already (milliseconds)
    public static final int CACHE_EXPIRY = 60 * 60 * 1000;
    // How long to look back beyond the previous query
    public static final int LOOKBACK_WINDOW = 30 * 1000;

    private final Logger logger = LoggerFactory.getLogger(
            InteractiveBrokersTradeReportRealTimeConsumer.class);

    private Cache<String, String> observedTradeKeys;
    private ScheduledExecutorService executorService;
    private int pollFrequency = DEFAULT_POLL_FREQUENCY;

    public InteractiveBrokersTradeReportRealTimeConsumer(
            InteractiveBrokersEndpoint endpoint, Processor processor,
            InteractiveBrokersBinding binding) {

        super(endpoint, processor, binding);

        observedTradeKeys = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .expireAfterWrite(CACHE_EXPIRY, TimeUnit.MILLISECONDS)
                .build();

        ThreadPoolBuilder poolBuilder = new ThreadPoolBuilder(
                this.getEndpoint().getCamelContext());
        try {
            executorService = poolBuilder
                    .poolSize(5)
                    .maxPoolSize(5)
                    .maxQueueSize(100)
                    .buildScheduled("tradeReportPoller");
        } catch (Exception e) {
            throw new RuntimeCamelException("can't build executorService for thread pool", e);
        }

        pollFrequency = configuration.getTradeReportPollingFrequency();
    }

    class TradeReportPoller implements Runnable {

        ExecutionFilter ef = new ExecutionFilter();
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
        Calendar last;

        public TradeReportPoller() {

            // FIXME: the API code doesn't explicitly specify the
            // expected time zone
            df.setTimeZone(TimeZone.getTimeZone("UTC"));

            last = Calendar.getInstance();

        }

        public void run() {

            logger.trace("polling trade reports since {}", ef.time());
            ef.time(df.format(last.getTime()));
            getBinding().getApiController().reqExecutions(ef,
                     InteractiveBrokersTradeReportRealTimeConsumer.this);
            logger.trace("polling request sent");

            // prepare for next execution
            last = Calendar.getInstance();
            last.add(Calendar.MILLISECOND, -1 * LOOKBACK_WINDOW);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executorService.scheduleAtFixedRate(
                new TradeReportPoller(), 0,
                pollFrequency, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doStop() throws Exception {
        executorService.shutdown();
        super.doStop();
    }

    @Override
    public void tradeReport(final String tradeKey, Contract contract,
            Execution execution) {
        logger.trace("tradeReport: {} secId: {} avgPrice: {}",
            new Object[] {tradeKey, contract.secId(), execution.avgPrice()});

        if (observedTradeKeys.getIfPresent(tradeKey) != null) {
            // Trade already seen, don't process it again
            return;
        }
        observedTradeKeys.put(tradeKey, tradeKey);

        final Exchange exchange = getEndpoint().createExchange();
        Message in = exchange.getIn();

        in.setHeader(InteractiveBrokersConstants.TRADE_KEY, tradeKey);
        in.setHeader(InteractiveBrokersConstants.ACCOUNT_NUMBER, execution.acctNumber());
        in.setHeader(InteractiveBrokersConstants.CLIENT_ID, execution.clientId());
        in.setHeader(InteractiveBrokersConstants.CONTRACT, contract);

        in.setBody(execution);

        try {

            // Standard synchronous processing:
            //getProcessor().process(exchange);

            // Support for asynchronous processing
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // noop
                    if (log.isTraceEnabled()) {
                        log.trace("Done processing trade report: {} {}", tradeKey,
                                doneSync ? "synchronously" : "asynchronously");
                    }
                }
            });

        } catch (Exception e) {
            handleException(String.format("Error processing %s: %s",
                    exchange, e.getMessage()), e);
        } finally {
            Exception ex = exchange.getException();
            if (ex != null) {
                handleException(String.format("Unhandled exception: %s",
                        ex.getMessage()), ex);
            }
        }
    }

    @Override
    public void tradeReportEnd() {
        logger.trace("end of trade report");
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
        // TODO Auto-generated method stub
    }
}
