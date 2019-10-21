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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.login.Configuration;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.IOHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

public final class HdfsConsumer extends ScheduledPollConsumer {

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
            log.info("Connecting to hdfs file-system {} (may take a while if connection is not available)", hdfsFsDescription);
        } else {
            log.debug("Connecting to hdfs file-system {} (may take a while if connection is not available)", hdfsFsDescription);
        }

        // hadoop will cache the connection by default so its faster to get in the poll method
        HdfsInfo answer = hdfsInfoFactory.newHdfsInfo(this.hdfsPath.toString());

        if (onStartup) {
            log.info("Connected to hdfs file-system {}", hdfsFsDescription);
        } else {
            log.debug("Connected to hdfs file-system {}", hdfsFsDescription);
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
        final AtomicInteger messageCount = new AtomicInteger(0);

        Arrays.stream(fileStatuses)
                .filter(status -> normalFileIsDirectoryHasSuccessFile(status, info))
                .filter(this::hasMatchingOwner)
                .map(this::createInputStream)
                .filter(Objects::nonNull)
                .forEach(hdfsInputStream -> {
                    try {
                        processHdfsInputStream(hdfsInputStream, messageCount, fileStatuses.length);
                    } finally {
                        IOHelper.close(hdfsInputStream, "input stream", log);
                    }
                });

        return messageCount.get();
    }

    private void processHdfsInputStream(HdfsInputStream inputStream, AtomicInteger messageCount, int totalFiles) {
        Holder<Object> key = new Holder<>();
        Holder<Object> value = new Holder<>();
        while (inputStream.next(key, value) >= 0) {
            Exchange exchange = this.getEndpoint().createExchange();
            Message message = exchange.getIn();
            String fileName = StringUtils.substringAfterLast(inputStream.getActualPath(), "/");
            message.setHeader(Exchange.FILE_NAME, fileName);
            message.setHeader(Exchange.FILE_NAME_CONSUMED, fileName);
            message.setHeader("CamelFileAbsolutePath", inputStream.getActualPath());
            if (key.value != null) {
                message.setHeader(HdfsHeader.KEY.name(), key.value);
            }

            if (inputStream.getNumOfReadBytes() >= 0) {
                message.setHeader(Exchange.FILE_LENGTH, inputStream.getNumOfReadBytes());
            }

            message.setBody(value.value);

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

            int count = messageCount.incrementAndGet();
            log.debug("Processed [{}] files out of [{}]", count, totalFiles);
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
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private boolean hasMatchingOwner(FileStatus fileStatus) {
        if (endpointConfig.getOwner() != null && !endpointConfig.getOwner().equals(fileStatus.getOwner())) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping file: {} as not matching owner: {}", fileStatus.getPath(), endpointConfig.getOwner());
            }
            return false;
        }
        return true;
    }

    private HdfsInputStream createInputStream(FileStatus fileStatus) {
        try {
            this.rwLock.writeLock().lock();
            return HdfsInputStream.createInputStream(fileStatus.getPath().toString(), hdfsInfoFactory);
        } finally {
            this.rwLock.writeLock().unlock();
        }
    }

}
