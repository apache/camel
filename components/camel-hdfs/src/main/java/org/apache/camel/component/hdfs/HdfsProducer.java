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
package org.apache.camel.component.hdfs;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.login.Configuration;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HdfsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(HdfsProducer.class);

    private final HdfsConfiguration config;
    private final StringBuilder hdfsPath;
    private final AtomicBoolean idle = new AtomicBoolean(false);
    private volatile ScheduledExecutorService scheduler;
    private volatile HdfsOutputStream oStream;

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
    public HdfsEndpoint getEndpoint() {
        return (HdfsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() {
        // need to remember auth as Hadoop will override that, which otherwise means the Auth is broken afterwards
        Configuration auth = HdfsComponent.getJAASConfiguration();
        try {
            super.doStart();

            // setup hdfs if configured to do on startup
            if (getEndpoint().getConfig().isConnectOnStartup()) {
                oStream = setupHdfs(true);
            }

            Optional<SplitStrategy> idleStrategy = tryFindIdleStrategy(config.getSplitStrategies());
            if (idleStrategy.isPresent()) {
                scheduler = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "HdfsIdleCheck");
                LOG.debug("Creating IdleCheck task scheduled to run every {} millis", config.getCheckIdleInterval());
                scheduler.scheduleAtFixedRate(new IdleCheck(idleStrategy.get()), config.getCheckIdleInterval(), config.getCheckIdleInterval(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            LOG.warn("Failed to start the HDFS producer. Caused by: [{}]", e.getMessage());
            LOG.debug("", e);
            throw new RuntimeCamelException(e);
        } finally {
            HdfsComponent.setJAASConfiguration(auth);
        }
    }

    private synchronized HdfsOutputStream setupHdfs(boolean onStartup) throws IOException {
        if (oStream != null) {
            return oStream;
        }

        StringBuilder actualPath = new StringBuilder(hdfsPath);
        if (config.hasSplitStrategies()) {
            actualPath = newFileName();
        }

        String hdfsFsDescription = config.getFileSystemLabel(actualPath.toString());

        // if we are starting up then log at info level, and if runtime then log at debug level to not flood the log
        if (onStartup) {
            LOG.info("Connecting to hdfs file-system {} (may take a while if connection is not available)", hdfsFsDescription);
        } else {
            LOG.debug("Connecting to hdfs file-system {} (may take a while if connection is not available)", hdfsFsDescription);
        }

        HdfsInfoFactory hdfsInfoFactory = new HdfsInfoFactory(config);
        HdfsOutputStream answer = HdfsOutputStream.createOutputStream(actualPath.toString(), hdfsInfoFactory);

        if (onStartup) {
            LOG.info("Connected to hdfs file-system {}", hdfsFsDescription);
        } else {
            LOG.debug("Connected to hdfs file-system {}", hdfsFsDescription);
        }

        return answer;
    }

    private Optional<SplitStrategy> tryFindIdleStrategy(List<SplitStrategy> strategies) {
        for (SplitStrategy strategy : strategies) {
            if (strategy.type == SplitStrategyType.IDLE) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (scheduler != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(scheduler);
            scheduler = null;
        }
        if (oStream != null) {
            IOHelper.close(oStream, "output stream", LOG);
            oStream = null;
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // need to remember auth as Hadoop will override that, which otherwise means the Auth is broken afterwards
        Configuration auth = HdfsComponent.getJAASConfiguration();
        try {
            doProcess(exchange);
        } finally {
            HdfsComponent.setJAASConfiguration(auth);
        }
    }

    void doProcess(Exchange exchange) throws IOException {
        Object body = exchange.getIn().getBody();
        Object key = exchange.getIn().getHeader(HdfsHeader.KEY.name());

        HdfsInfoFactory hdfsInfoFactory = new HdfsInfoFactory(config);
        // if an explicit filename is specified, close any existing stream and append the filename to the hdfsPath
        if (exchange.getIn().getHeader(Exchange.FILE_NAME) != null) {
            if (oStream != null) {
                IOHelper.close(oStream, "output stream", LOG);
            }
            StringBuilder actualPath = getHdfsPathUsingFileNameHeader(exchange);
            oStream = HdfsOutputStream.createOutputStream(actualPath.toString(), hdfsInfoFactory);
        } else if (oStream == null) {
            // must have oStream
            oStream = setupHdfs(false);
        }

        if (isSplitRequired(config.getSplitStrategies())) {
            if (oStream != null) {
                IOHelper.close(oStream, "output stream", LOG);
            }
            StringBuilder actualPath = newFileName();
            oStream = HdfsOutputStream.createOutputStream(actualPath.toString(), hdfsInfoFactory);
        }

        String path = oStream.getActualPath();
        LOG.trace("Writing body to hdfs-file {}", path);
        oStream.append(key, body, exchange);

        idle.set(false);

        // close if we do not have idle checker task to do this for us
        boolean close = scheduler == null;
        // but user may have a header to explicit control the close
        Boolean closeHeader = exchange.getIn().getHeader(HdfsConstants.HDFS_CLOSE, Boolean.class);
        if (closeHeader != null) {
            close = closeHeader;
        }

        // if no idle checker then we need to explicit close the stream after usage
        if (close) {
            try {
                LOG.trace("Closing stream");
                oStream.close();
                oStream = null;
            } catch (IOException e) {
                // ignore
            }
        }

        LOG.debug("Wrote body to hdfs-file {}", path);
    }

    /**
     * helper method to construct the hdfsPath from the CamelFileName String or Expression
     */
    private StringBuilder getHdfsPathUsingFileNameHeader(Exchange exchange) {
        StringBuilder actualPath = new StringBuilder(hdfsPath);
        String fileName = "";
        Object value = exchange.getIn().getHeader(Exchange.FILE_NAME);
        if (value instanceof String) {
            fileName = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
        } else if (value instanceof Expression) {
            fileName = ((Expression) value).evaluate(exchange, String.class);
        }
        return actualPath.append(fileName);
    }

    private boolean isSplitRequired(List<SplitStrategy> strategies) {
        boolean split = false;
        for (SplitStrategy splitStrategy : strategies) {
            split |= splitStrategy.getType().split(oStream, splitStrategy.value, this);
        }
        return split;
    }

    private StringBuilder newFileName() {
        StringBuilder actualPath = new StringBuilder(hdfsPath);
        actualPath.append(StringHelper.sanitize(getEndpoint().getCamelContext().getUuidGenerator().generateUuid()));
        return actualPath;
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
            // only run if oStream has been created
            if (oStream == null) {
                return;
            }

            LOG.trace("IdleCheck running");

            if (System.currentTimeMillis() - oStream.getLastAccess() > strategy.value && !idle.get() && !oStream.isBusy().get()) {
                idle.set(true);
                try {
                    LOG.trace("Closing stream as idle");
                    oStream.close();
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

