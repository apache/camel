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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import javax.security.auth.login.Configuration;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.IOHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HdfsConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HdfsConsumer.class);

    private final HdfsConfiguration endpointConfig;
    private final StringBuilder hdfsPath;
    private final Processor processor;
    private final HdfsInfoFactory hdfsInfoFactory;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public HdfsConsumer(HdfsEndpoint endpoint, Processor processor, HdfsConfiguration endpointConfig) {
        this(endpoint, processor, endpointConfig, new HdfsInfoFactory(endpointConfig), endpointConfig.getFileSystemType().getHdfsPath(endpointConfig));
    }

    HdfsConsumer(HdfsEndpoint endpoint, Processor processor, HdfsConfiguration endpointConfig, HdfsInfoFactory hdfsInfoFactory, StringBuilder hdfsPath) {
        super(endpoint, processor);
        this.processor = processor;
        this.endpointConfig = endpointConfig;
        this.hdfsPath = hdfsPath;
        this.hdfsInfoFactory = hdfsInfoFactory;
        setUseFixedDelay(true);
    }

    @Override
    public HdfsEndpoint getEndpoint() {
        return (HdfsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (endpointConfig.isConnectOnStartup()) {
            // setup hdfs if configured to do on startup
            setupHdfs(true);
        }
    }

    private HdfsInfo setupHdfs(boolean onStartup) throws IOException {
        String hdfsFsDescription = endpointConfig.getFileSystemLabel(hdfsPath.toString());
        // if we are starting up then log at info level, and if runtime then log at debug level to not flood the log
        if (onStartup) {
            LOG.info("Connecting to hdfs file-system {} (may take a while if connection is not available)", hdfsFsDescription);
        } else {
            LOG.debug("Connecting to hdfs file-system {} (may take a while if connection is not available)", hdfsFsDescription);
        }

        // hadoop will cache the connection by default so its faster to get in the poll method
        HdfsInfo answer = hdfsInfoFactory.newHdfsInfo(this.hdfsPath.toString());

        if (onStartup) {
            LOG.info("Connected to hdfs file-system {}", hdfsFsDescription);
        } else {
            LOG.debug("Connected to hdfs file-system {}", hdfsFsDescription);
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

    protected int doPoll() throws IOException {
        class ExcludePathFilter implements PathFilter {
            @Override
            public boolean accept(Path path) {
                return !(path.toString().endsWith(endpointConfig.getOpenedSuffix()) || path.toString().endsWith(endpointConfig.getReadSuffix()));
            }
        }

        HdfsInfo info = setupHdfs(false);
        FileStatus[] fileStatuses;
        if (info.getFileSystem().isFile(info.getPath())) {
            fileStatuses = info.getFileSystem().globStatus(info.getPath());
        } else {
            Path pattern = info.getPath().suffix("/" + this.endpointConfig.getPattern());
            fileStatuses = info.getFileSystem().globStatus(pattern, new ExcludePathFilter());
        }

        fileStatuses = Optional.ofNullable(fileStatuses).orElse(new FileStatus[0]);

        return processFileStatuses(info, fileStatuses);
    }

    private int processFileStatuses(HdfsInfo info, FileStatus[] fileStatuses) {
        final AtomicInteger totalMessageCount = new AtomicInteger(0);

        List<HdfsInputStream> hdfsFiles = Arrays.stream(fileStatuses)
                .filter(status -> normalFileIsDirectoryHasSuccessFile(status, info))
                .filter(this::hasMatchingOwner)
                .limit(endpointConfig.getMaxMessagesPerPoll())
                .map(this::asHdfsFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LOG.info("Processing [{}] valid files out of [{}] available.", hdfsFiles.size(), fileStatuses.length);

        for (int i = 0; i < hdfsFiles.size(); i++) {
            HdfsInputStream hdfsFile = hdfsFiles.get(i);
            try {
                int messageCount = processHdfsInputStream(hdfsFile, totalMessageCount);
                LOG.debug("Processed [{}] files out of [{}].", i, hdfsFiles.size());
                LOG.debug("File [{}] was split to [{}] messages.", i, messageCount);
            } finally {
                IOHelper.close(hdfsFile, "hdfs file", LOG);
            }
        }

        return totalMessageCount.get();
    }

    private int processHdfsInputStream(HdfsInputStream hdfsFile, AtomicInteger totalMessageCount) {
        final AtomicInteger messageCount = new AtomicInteger(0);
        Holder<Object> currentKey = new Holder<>();
        Holder<Object> currentValue = new Holder<>();

        while (hdfsFile.next(currentKey, currentValue) >= 0) {
            processHdfsInputStream(hdfsFile, currentKey, currentValue, messageCount, totalMessageCount);
            messageCount.incrementAndGet();
        }

        return messageCount.get();
    }

    private void processHdfsInputStream(HdfsInputStream hdfsFile, Holder<Object> key, Holder<Object> value, AtomicInteger messageCount, AtomicInteger totalMessageCount) {
        Exchange exchange = this.getEndpoint().createExchange();
        Message message = exchange.getIn();
        String fileName = StringUtils.substringAfterLast(hdfsFile.getActualPath(), "/");
        message.setHeader(Exchange.FILE_NAME, fileName);
        message.setHeader(Exchange.FILE_NAME_CONSUMED, fileName);
        message.setHeader("CamelFileAbsolutePath", hdfsFile.getActualPath());
        if (key.getValue() != null) {
            message.setHeader(HdfsHeader.KEY.name(), key.getValue());
        }

        if (hdfsFile.getNumOfReadBytes() >= 0) {
            message.setHeader(Exchange.FILE_LENGTH, hdfsFile.getNumOfReadBytes());
        }

        message.setBody(value.getValue());

        updateNewExchange(exchange, messageCount.get(), hdfsFile);

        LOG.debug("Processing file [{}]", fileName);
        try {
            processor.process(exchange);
            totalMessageCount.incrementAndGet();
        } catch (Exception e) {
            exchange.setException(e);
        }

        // in case of unhandled exceptions then let the exception handler handle them
        if (exchange.getException() != null) {
            getExceptionHandler().handleException(exchange.getException());
        }
    }

    private boolean normalFileIsDirectoryHasSuccessFile(FileStatus fileStatus, HdfsInfo info) {
        if (endpointConfig.getFileType().equals(HdfsFileType.NORMAL_FILE) && fileStatus.isDirectory()) {
            try {
                Path successPath = new Path(fileStatus.getPath().toString() + "/_SUCCESS");
                if (!info.getFileSystem().exists(successPath)) {
                    return false;
                }
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
        }
        return true;
    }

    private boolean hasMatchingOwner(FileStatus fileStatus) {
        if (endpointConfig.getOwner() != null && !endpointConfig.getOwner().equals(fileStatus.getOwner())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Skipping file: {} as not matching owner: {}", fileStatus.getPath(), endpointConfig.getOwner());
            }
            return false;
        }
        return true;
    }

    private HdfsInputStream asHdfsFile(FileStatus fileStatus) {
        try {
            this.rwLock.writeLock().lock();
            return HdfsInputStream.createInputStream(fileStatus.getPath().toString(), hdfsInfoFactory);
        } finally {
            this.rwLock.writeLock().unlock();
        }
    }

    protected void updateNewExchange(Exchange exchange, int index, HdfsInputStream hdfsFile) {
        // do not share unit of work
        exchange.adapt(ExtendedExchange.class).setUnitOfWork(null);

        exchange.setProperty(Exchange.SPLIT_INDEX, index);

        if (hdfsFile.hasNext()) {
            exchange.setProperty(Exchange.SPLIT_COMPLETE, Boolean.FALSE);
        } else {
            exchange.setProperty(Exchange.SPLIT_COMPLETE, Boolean.TRUE);
            // streaming mode, so set total size when we are complete based on the index
            exchange.setProperty(Exchange.SPLIT_SIZE, index + 1);
        }
    }

}
