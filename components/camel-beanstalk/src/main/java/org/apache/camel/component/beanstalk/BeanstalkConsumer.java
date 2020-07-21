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
package org.apache.camel.component.beanstalk;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import com.surftools.BeanstalkClient.BeanstalkException;
import com.surftools.BeanstalkClient.Client;
import com.surftools.BeanstalkClient.Job;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.beanstalk.processors.BuryCommand;
import org.apache.camel.component.beanstalk.processors.Command;
import org.apache.camel.component.beanstalk.processors.DeleteCommand;
import org.apache.camel.component.beanstalk.processors.ReleaseCommand;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PollingConsumer to read Beanstalk jobs.
 * <p/>
 * The consumer may delete the job immediately or based on successful {@link Exchange}
 * completion. The behavior is configurable by <code>consumer.awaitJob</code>
 * flag (by default <code>true</code>)
 * <p/>
 * This consumer will add a {@link Synchronization} object to every {@link Exchange}
 * object it creates in order to react on successful exchange completion or failure.
 * <p/>
 * In the case of successful completion, Beanstalk's <code>delete</code> method is
 * called upon the job. In the case of failure the default reaction is to call
 * <code>bury</code>.
 * <p/>
 * The reaction on failures is configurable: possible variants are "bury", "release" or "delete"
 */
public class BeanstalkConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(BeanstalkConsumer.class);

    private static final String[] STATS_KEY_STR = new String[]{"tube", "state"};
    private static final String[] STATS_KEY_INT = new String[]{"age", "time-left", "timeouts", "releases", "buries", "kicks"};

    private BeanstalkCommand onFailure;
    private boolean useBlockIO;
    private boolean awaitJob;
    private Client client;
    private ExecutorService executor;
    private Synchronization sync;

    private final Runnable initTask = new Runnable() {
        @Override
        public void run() {
            client = getEndpoint().getConnection().newReadingClient(useBlockIO);
        }
    };

    private final Callable<Exchange> pollTask = new Callable<Exchange>() {
        final Integer noWait = 0;

        @Override
        public Exchange call() throws Exception {
            if (client == null) {
                throw new RuntimeCamelException("Beanstalk client not initialized");
            }

            try {
                final Job job = client.reserve(noWait);
                if (job == null) {
                    return null;
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Received job ID %d (data length %d)", job.getJobId(), job.getData().length));
                }

                final Exchange exchange = getEndpoint().createExchange(ExchangePattern.InOnly);
                exchange.getIn().setHeader(Headers.JOB_ID, job.getJobId());
                exchange.getIn().setBody(job.getData(), byte[].class);

                Map<String, String> jobStats = client.statsJob(job.getJobId());
                if (jobStats != null && !jobStats.isEmpty()) {
                    for (String key : STATS_KEY_STR) {
                        if (jobStats.containsKey(key)) {
                            exchange.getIn().setHeader(Headers.PREFIX + key, jobStats.get(key).trim());
                        }
                    }

                    if (jobStats.containsKey("pri")) {
                        exchange.getIn().setHeader(Headers.PRIORITY, Long.parseLong(jobStats.get("pri").trim()));
                    }

                    for (String key : STATS_KEY_INT) {
                        if (jobStats.containsKey(key)) {
                            exchange.getIn().setHeader(Headers.PREFIX + key, Integer.parseInt(jobStats.get(key).trim()));
                        }
                    }
                }

                if (!awaitJob) {
                    client.delete(job.getJobId());
                } else {
                    exchange.adapt(ExtendedExchange.class).addOnCompletion(sync);
                }

                return exchange;
            } catch (BeanstalkException e) {
                getExceptionHandler().handleException("Beanstalk client error", e);
                resetClient();
                return null;
            }
        }
    };

    public BeanstalkConsumer(final BeanstalkEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        int messagesPolled = 0;
        while (isPollAllowed()) {
            final Exchange exchange = executor.submit(pollTask).get();
            if (exchange == null) {
                break;
            }

            ++messagesPolled;
            getProcessor().process(exchange);
        }
        return messagesPolled;
    }

    public BeanstalkCommand getOnFailure() {
        return onFailure;
    }

    public void setOnFailure(BeanstalkCommand onFailure) {
        this.onFailure = onFailure;
    }

    public boolean isUseBlockIO() {
        return useBlockIO;
    }

    public void setUseBlockIO(boolean useBlockIO) {
        this.useBlockIO = useBlockIO;
    }

    public boolean isAwaitJob() {
        return awaitJob;
    }

    public void setAwaitJob(boolean awaitJob) {
        this.awaitJob = awaitJob;
    }

    @Override
    public BeanstalkEndpoint getEndpoint() {
        return (BeanstalkEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        executor = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "Beanstalk-Consumer");
        executor.execute(initTask);
        sync = new Sync();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (executor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(executor);
        }
    }

    protected void resetClient() {
        if (client != null) {
            client.close();
        }
        initTask.run();
    }

    class Sync implements Synchronization {
        protected final Command successCommand;
        protected final Command failureCommand;

        Sync() {
            successCommand = new DeleteCommand(getEndpoint());

            if (BeanstalkComponent.COMMAND_BURY.equals(onFailure.name())) {
                failureCommand = new BuryCommand(getEndpoint());
            } else if (BeanstalkComponent.COMMAND_RELEASE.equals(onFailure.name())) {
                failureCommand = new ReleaseCommand(getEndpoint());
            } else if (BeanstalkComponent.COMMAND_DELETE.equals(onFailure.name())) {
                failureCommand = new DeleteCommand(getEndpoint());
            } else {
                throw new IllegalArgumentException(String.format("Unknown failure command: %s", onFailure));
            }
        }

        @Override
        public void onComplete(final Exchange exchange) {
            try {
                executor.submit(new RunCommand(successCommand, exchange)).get();
            } catch (Exception e) {
                LOG.error(String.format("Could not run completion of exchange %s", exchange), e);
            }
        }

        @Override
        public void onFailure(final Exchange exchange) {
            try {
                executor.submit(new RunCommand(failureCommand, exchange)).get();
            } catch (Exception e) {
                LOG.error(String.format("%s could not run failure of exchange %s", failureCommand.getClass().getName(), exchange), e);
            }
        }

        class RunCommand implements Runnable {
            private final Command command;
            private final Exchange exchange;

            RunCommand(final Command command, final Exchange exchange) {
                this.command = command;
                this.exchange = exchange;
            }

            @Override
            public void run() {
                try {
                    try {
                        command.act(client, exchange);
                    } catch (BeanstalkException e) {
                        LOG.warn(String.format("Post-processing %s of exchange %s failed, retrying.", command.getClass().getName(), exchange), e);
                        resetClient();
                        command.act(client, exchange);
                    }
                } catch (final Exception e) {
                    LOG.error(String.format("%s could not post-process exchange %s", command.getClass().getName(), exchange), e);
                    exchange.setException(e);
                }
            }
        }
    }
}
