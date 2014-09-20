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
package org.apache.camel.component.hdfs2;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.login.Configuration;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

public class HdfsProducer extends DefaultProducer {

    private final HdfsConfiguration config;
    private final StringBuilder hdfsPath;
    private final AtomicBoolean idle = new AtomicBoolean(false);
    private volatile ScheduledExecutorService scheduler;
    private volatile HdfsOutputStream ostream;

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
    protected void doStart() throws Exception {
        // need to remember auth as Hadoop will override that, which otherwise means the Auth is broken afterwards
        Configuration auth = HdfsComponent.getJAASConfiguration();
        try {
            super.doStart();

            // setup hdfs if configured to do on startup
            if (getEndpoint().getConfig().isConnectOnStartup()) {
                ostream = setupHdfs(true);
            }

            SplitStrategy idleStrategy = null;
            for (SplitStrategy strategy : config.getSplitStrategies()) {
                if (strategy.type == SplitStrategyType.IDLE) {
                    idleStrategy = strategy;
                    break;
                }
            }
            if (idleStrategy != null) {
                scheduler = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "HdfsIdleCheck");
                log.debug("Creating IdleCheck task scheduled to run every {} millis", config.getCheckIdleInterval());
                scheduler.scheduleAtFixedRate(new IdleCheck(idleStrategy), config.getCheckIdleInterval(), config.getCheckIdleInterval(), TimeUnit.MILLISECONDS);
            }
        } finally {
            HdfsComponent.setJAASConfiguration(auth);
        }
    }

    private synchronized HdfsOutputStream setupHdfs(boolean onStartup) throws Exception {
        if (ostream != null) {
            return ostream;
        }

        StringBuilder actualPath = new StringBuilder(hdfsPath);
        if (config.getSplitStrategies().size() > 0) {
            actualPath = newFileName();
        }

        // if we are starting up then log at info level, and if runtime then log at debug level to not flood the log
        if (onStartup) {
            log.info("Connecting to hdfs file-system {}:{}/{} (may take a while if connection is not available)", new Object[]{config.getHostName(), config.getPort(), actualPath.toString()});
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Connecting to hdfs file-system {}:{}/{} (may take a while if connection is not available)", new Object[]{config.getHostName(), config.getPort(), actualPath.toString()});
            }
        }

        HdfsOutputStream answer = HdfsOutputStream.createOutputStream(actualPath.toString(), config);

        if (onStartup) {
            log.info("Connected to hdfs file-system {}:{}/{}", new Object[]{config.getHostName(), config.getPort(), actualPath.toString()});
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Connected to hdfs file-system {}:{}/{}", new Object[]{config.getHostName(), config.getPort(), actualPath.toString()});
            }
        }

        return answer;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (scheduler != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(scheduler);
            scheduler = null;
        }
        if (ostream != null) {
            IOHelper.close(ostream, "output stream", log);
            ostream = null;
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

    void doProcess(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        Object key = exchange.getIn().getHeader(HdfsHeader.KEY.name());

        // if an explicit filename is specified, close any existing stream and append the filename to the hdfsPath
        if (exchange.getIn().getHeader(Exchange.FILE_NAME) != null) {
            if (ostream != null) {
                IOHelper.close(ostream, "output stream", log);
            }
            StringBuilder actualPath = getHdfsPathUsingFileNameHeader(exchange);
            ostream = HdfsOutputStream.createOutputStream(actualPath.toString(), config);
        } else if (ostream == null) {
            // must have ostream
            ostream = setupHdfs(false);
        }

        boolean split = false;
        List<SplitStrategy> strategies = config.getSplitStrategies();
        for (SplitStrategy splitStrategy : strategies) {
            split |= splitStrategy.getType().split(ostream, splitStrategy.value, this);
        }

        if (split) {
            if (ostream != null) {
                IOHelper.close(ostream, "output stream", log);
            }
            StringBuilder actualPath = newFileName();
            ostream = HdfsOutputStream.createOutputStream(actualPath.toString(), config);
        }

        String path = ostream.getActualPath();
        log.trace("Writing body to hdfs-file {}", path);
        ostream.append(key, body, exchange.getContext().getTypeConverter());

        idle.set(false);

        // close if we do not have idle checker task to do this for us
        boolean close = scheduler == null;
        // but user may have a header to explict control the close
        Boolean closeHeader = exchange.getIn().getHeader(HdfsConstants.HDFS_CLOSE, Boolean.class);
        if (closeHeader != null) {
            close = closeHeader;
        }

        // if no idle checker then we need to explicit close the stream after usage
        if (close) {
            try {
                HdfsProducer.this.log.trace("Closing stream");
                ostream.close();
                ostream = null;
            } catch (IOException e) {
                // ignore
            }
        }

        log.debug("Wrote body to hdfs-file {}", path);
    }

    /**
     * helper method to construct the hdfsPath from the CamelFileName String or Expression
     * @param exchange
     * @return
     */
    private StringBuilder getHdfsPathUsingFileNameHeader(Exchange exchange) {
        StringBuilder actualPath = new StringBuilder(hdfsPath);
        String fileName = "";
        Object value = exchange.getIn().getHeader(Exchange.FILE_NAME);
        if (value instanceof String) {
            fileName = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
        } else if (value instanceof Expression) {
            fileName =  ((Expression) value).evaluate(exchange, String.class);
        }
        return actualPath.append(fileName);
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
            // only run if ostream has been created
            if (ostream == null) {
                return;
            }

            HdfsProducer.this.log.trace("IdleCheck running");

            if (System.currentTimeMillis() - ostream.getLastAccess() > strategy.value && !idle.get() && !ostream.isBusy().get()) {
                idle.set(true);
                try {
                    HdfsProducer.this.log.trace("Closing stream as idle");
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

