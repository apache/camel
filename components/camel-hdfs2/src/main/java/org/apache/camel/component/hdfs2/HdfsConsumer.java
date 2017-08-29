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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.login.Configuration;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.util.IOHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

public final class HdfsConsumer extends ScheduledPollConsumer {

    public static final long DEFAULT_CONSUMER_INITIAL_DELAY = 10 * 1000L;

    private final HdfsConfiguration config;
    private final StringBuilder hdfsPath;
    private final Processor processor;
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private volatile HdfsInputStream istream;
    
    public HdfsConsumer(HdfsEndpoint endpoint, Processor processor, HdfsConfiguration config) {
        super(endpoint, processor);
        this.config = config;
        this.hdfsPath = config.getFileSystemType().getHdfsPath(config);
        this.processor = processor;
        setUseFixedDelay(true);
    }

    @Override
    public HdfsEndpoint getEndpoint() {
        return (HdfsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (config.isConnectOnStartup()) {
            // setup hdfs if configured to do on startup
            setupHdfs(true);
        }
    }

    private HdfsInfo setupHdfs(boolean onStartup) throws Exception {
        // if we are starting up then log at info level, and if runtime then log at debug level to not flood the log
        if (onStartup) {
            log.info("Connecting to hdfs file-system {}:{}/{} (may take a while if connection is not available)", new Object[]{config.getHostName(), config.getPort(), hdfsPath.toString()});
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Connecting to hdfs file-system {}:{}/{} (may take a while if connection is not available)", new Object[]{config.getHostName(), config.getPort(), hdfsPath.toString()});
            }
        }

        // hadoop will cache the connection by default so its faster to get in the poll method
        HdfsInfo answer = HdfsInfoFactory.newHdfsInfo(this.hdfsPath.toString());

        if (onStartup) {
            log.info("Connected to hdfs file-system {}:{}/{}", new Object[]{config.getHostName(), config.getPort(), hdfsPath.toString()});
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Connected to hdfs file-system {}:{}/{}", new Object[]{config.getHostName(), config.getPort(), hdfsPath.toString()});
            }
        }
        return answer;
    }

    @Override
    protected int poll() throws Exception {
        // need to remember auth as Hadoop will override that, which otherwise means the Auth is broken afterwards
        Configuration auth = HdfsComponent.getJAASConfiguration();
        try {
            return doPoll();
        } finally {
            HdfsComponent.setJAASConfiguration(auth);
        }
    }

    protected int doPoll() throws Exception {
        class ExcludePathFilter implements PathFilter {
            public boolean accept(Path path) {
                return !(path.toString().endsWith(config.getOpenedSuffix()) || path.toString().endsWith(config.getReadSuffix()));
            }
        }

        int numMessages = 0;

        HdfsInfo info = setupHdfs(false);
        FileStatus fileStatuses[];
        if (info.getFileSystem().isFile(info.getPath())) {
            fileStatuses = info.getFileSystem().globStatus(info.getPath());
        } else {
            Path pattern = info.getPath().suffix("/" + this.config.getPattern());
            fileStatuses = info.getFileSystem().globStatus(pattern, new ExcludePathFilter());
        }

        for (FileStatus status : fileStatuses) {

            if (normalFileIsDirectoryNoSuccessFile(status, info)) {
                continue;
            }

            if (config.getOwner() != null) {
                // must match owner
                if (!config.getOwner().equals(status.getOwner())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping file: {} as not matching owner: {}", status.getPath().toString(), config.getOwner());
                    }
                    continue;
                }
            }

            try {
                this.rwlock.writeLock().lock();
                this.istream = HdfsInputStream.createInputStream(status.getPath().toString(), this.config);
                if (!this.istream.isOpened()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping file: {} because it doesn't exist anymore", status.getPath().toString());
                    }
                    continue;
                }
            } finally {
                this.rwlock.writeLock().unlock();
            }

            try {
                Holder<Object> key = new Holder<Object>();
                Holder<Object> value = new Holder<Object>();
                while (this.istream.next(key, value) >= 0) {
                    Exchange exchange = this.getEndpoint().createExchange();
                    Message message = new DefaultMessage(this.getEndpoint().getCamelContext());
                    String fileName = StringUtils.substringAfterLast(status.getPath().toString(), "/");
                    message.setHeader(Exchange.FILE_NAME, fileName);
                    if (key.value != null) {
                        message.setHeader(HdfsHeader.KEY.name(), key.value);
                    }
                    message.setBody(value.value);
                    exchange.setIn(message);

                    log.debug("Processing file {}", fileName);
                    try {
                        processor.process(exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    }

                    // in case of unhandled exceptions then let the exception handler handle them
                    if (exchange.getException() != null) {
                        getExceptionHandler().handleException(exchange.getException());
                    }

                    numMessages++;
                }
            } finally {
                IOHelper.close(istream, "input stream", log);
            }
        }

        return numMessages;
    }

    private boolean normalFileIsDirectoryNoSuccessFile(FileStatus status, HdfsInfo info) throws IOException {
        if (config.getFileType().equals(HdfsFileType.NORMAL_FILE) && status.isDirectory()) {
            Path successPath = new Path(status.getPath().toString() + "/_SUCCESS");
            if (!info.getFileSystem().exists(successPath)) {
                return true;
            }
        }
        return false;
    }

}
