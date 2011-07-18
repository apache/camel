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
package org.apache.camel.component.hdfs;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

public class HdfsProducer extends DefaultProducer {

    private final HdfsConfiguration config;
    private final StringBuilder hdfsPath;
    private final AtomicBoolean idle = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private HdfsOutputStream ostream;
    private long splitNum;

    public static final class SplitStrategy {
        private SplitStrategyType type;
        private long value;

        public SplitStrategy(SplitStrategyType type, long value) {
            this.type = type;
            this.value = value;
        }

        public SplitStrategyType getType() {
            return type;
        }

        public long getValue() {
            return value;
        }
    }

    public enum SplitStrategyType {
        BYTES {
            @Override
            public boolean split(HdfsOutputStream oldOstream, long value, HdfsProducer producer) {
                return oldOstream.getNumOfWrittenBytes() >= value;
            }
        },

        MESSAGES {
            @Override
            public boolean split(HdfsOutputStream oldOstream, long value, HdfsProducer producer) {
                return oldOstream.getNumOfWrittenMessages() >= value;
            }
        },

        IDLE {
            @Override
            public boolean split(HdfsOutputStream oldOstream, long value, HdfsProducer producer) {
                return producer.idle.get();
            }
        };

        public abstract boolean split(HdfsOutputStream oldOstream, long value, HdfsProducer producer);
    }

    public HdfsProducer(HdfsEndpoint endpoint, HdfsConfiguration config) {
        super(endpoint);
        this.config = config;
        this.hdfsPath = config.getFileSystemType().getHdfsPath(config);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        StringBuilder actualPath = new StringBuilder(hdfsPath);
        if (config.getSplitStrategies().size() > 0) {
            actualPath = newFileName();
        }
        ostream = HdfsOutputStream.createOutputStream(actualPath.toString(), config);

        SplitStrategy idleStrategy = null;
        for (SplitStrategy strategy : config.getSplitStrategies()) {
            if (strategy.type == SplitStrategyType.IDLE) {
                idleStrategy = strategy;
                break;
            }
        }
        if (idleStrategy != null) {
            scheduler = getEndpoint().getCamelContext().getExecutorServiceStrategy().newScheduledThreadPool(this, "IdleCheck", 1);
            log.debug("Creating IdleCheck task scheduled to run every {} millis", config.getCheckIdleInterval());
            scheduler.scheduleAtFixedRate(new IdleCheck(idleStrategy), 1000, config.getCheckIdleInterval(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ostream.close();
        if (scheduler != null) {
            getEndpoint().getCamelContext().getExecutorServiceStrategy().shutdown(scheduler);
            scheduler = null;
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        Object key = exchange.getIn().getHeader(HdfsHeader.KEY.name());

        boolean split = false;
        List<SplitStrategy> strategies = config.getSplitStrategies();
        for (SplitStrategy splitStrategy : strategies) {
            split |= splitStrategy.getType().split(ostream, splitStrategy.value, this);
        }

        if (split) {
            ostream.close();
            StringBuilder actualPath = newFileName();
            ostream = HdfsOutputStream.createOutputStream(actualPath.toString(), config);
        }
        ostream.append(key, body, exchange.getContext().getTypeConverter());
        idle.set(false);
    }

    public HdfsOutputStream getOstream() {
        return ostream;
    }

    private StringBuilder newFileName() {
        StringBuilder actualPath = new StringBuilder(hdfsPath);
        actualPath.append(splitNum);
        splitNum++;
        return actualPath;
    }

    public final AtomicBoolean isIdle() {
        return idle;
    }

    /**
     * Idle check background task
     */
    private final class IdleCheck implements Runnable {

        private final SplitStrategy strategy;

        private IdleCheck(SplitStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public void run() {
            HdfsProducer.this.log.trace("IdleCheck running");

            if (System.currentTimeMillis() - ostream.getLastAccess() > strategy.value && !idle.get() && !ostream.isBusy().get()) {
                idle.set(true);
                try {
                    ostream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        @Override
        public String toString() {
            return "IdleCheck";
        }
    }
}

