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
package org.apache.camel.component.file.remote;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFilePollingConsumer;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote file endpoint.
 */
public abstract class RemoteFileEndpoint<T> extends GenericFileEndpoint<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteFileEndpoint.class);

    @UriParam(label = "advanced", description = "Specifies the maximum reconnect attempts Camel performs when it "
                                                + "tries to connect to the remote FTP server. Use 0 to disable this behavior.")
    private int maximumReconnectAttempts = 3;
    @UriParam(label = "advanced", defaultValue = "1000",
              description = "Delay in millis Camel will wait before performing a reconnect attempt.",
              javaType = "java.time.Duration")
    private long reconnectDelay = 1000;
    @UriParam(label = "common", description = "Whether or not to disconnect from remote FTP server right after use. "
                                              + "Disconnect will only disconnect the current connection to the FTP server. If you have a consumer which "
                                              + "you want to stop, then you need to stop the consumer/route instead.")
    private boolean disconnect;
    @UriParam(label = "producer,advanced", description = "Whether or not to disconnect from remote FTP server right "
                                                         + "after a Batch upload is complete. disconnectOnBatchComplete will only disconnect the current connection "
                                                         + "to the FTP server.")
    private boolean disconnectOnBatchComplete;
    @UriParam(label = "common,advanced", description = "If set this option to be true, camel-ftp will use the list "
                                                       + "file directly to check if the file exists. Since some FTP server may not support to list the file "
                                                       + "directly, if the option is false, camel-ftp will use the old way to list the directory and check if the "
                                                       + "file exists. This option also influences readLock=changed to control whether it performs a fast check "
                                                       + "to update file information or not. This can be used to speed up the process if the FTP server has a lot "
                                                       + "of files.")
    private boolean fastExistsCheck;
    @UriParam(label = "consumer,advanced", description = "Whether the FTP consumer should download the file. If this "
                                                         + "option is set to false, then the message body will be null, but the consumer will still trigger a Camel "
                                                         + "Exchange that has details about the file such as file name, file size, etc. It's just that the file will "
                                                         + "not be downloaded.")
    private boolean download = true;

    protected RemoteFileEndpoint() {
        // ftp must be synchronous as the ftp-client is not thread-safe
        setSynchronous(true);
        // no args constructor for spring bean endpoint configuration
        // for ftp we need to use higher interval/checkout that for files
        setReadLockTimeout(20000);
        setReadLockCheckInterval(5000);
        // explicitly set RemoteFilePollingConsumerPollStrategy otherwise
        // DefaultPollingConsumerPollStrategy is be used
        setPollStrategy(new RemoteFilePollingConsumerPollStrategy());
    }

    protected RemoteFileEndpoint(String uri, RemoteFileComponent<T> component, RemoteFileConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        // ftp must be synchronous as the ftp-client is not thread-safe
        setSynchronous(true);
        // for ftp we need to use higher interval/checkout that for files
        setReadLockTimeout(20000);
        setReadLockCheckInterval(5000);
        // explicitly set RemoteFilePollingConsumerPollStrategy otherwise
        // DefaultPollingConsumerPollStrategy is be used
        setPollStrategy(new RemoteFilePollingConsumerPollStrategy());
    }

    @Override
    public boolean isSingletonProducer() {
        // this producer is stateful because the remote file operations is not
        // thread safe
        return false;
    }

    @Override
    public RemoteFileConfiguration getConfiguration() {
        return (RemoteFileConfiguration) this.configuration;
    }

    @Override
    public Exchange createExchange(GenericFile<T> file) {
        Exchange answer = super.createExchange();
        if (file != null) {
            file.bindToExchange(answer);
        }
        return answer;
    }

    @Override
    public GenericFileProducer<T> createProducer() throws Exception {
        afterPropertiesSet();

        // you cannot use temp file and file exists append
        if (getFileExist() == GenericFileExist.Append && (getTempPrefix() != null || getTempFileName() != null)) {
            throw new IllegalArgumentException("You cannot set both fileExist=Append and tempPrefix/tempFileName options");
        }
        // ensure fileExist and moveExisting is configured correctly if in use
        if (getFileExist() == GenericFileExist.Move && getMoveExisting() == null) {
            throw new IllegalArgumentException("You must configure moveExisting option when fileExist=Move");
        } else if (getMoveExisting() != null && getFileExist() != GenericFileExist.Move) {
            throw new IllegalArgumentException("You must configure fileExist=Move when moveExisting has been set");
        }

        return buildProducer();
    }

    @Override
    public RemoteFileConsumer<T> createConsumer(Processor processor) throws Exception {
        afterPropertiesSet();
        RemoteFileConsumer<T> consumer = buildConsumer(processor);

        if (isDelete() && getMove() != null) {
            throw new IllegalArgumentException("You cannot both set delete=true and move options");
        }

        // if noop=true then idempotent should also be configured
        if (isNoop() && !isIdempotentSet()) {
            LOG.info("Endpoint is configured with noop=true so forcing endpoint to be idempotent as well");
            setIdempotent(true);
        }

        // if idempotent and no repository set then create a default one
        if (isIdempotentSet() && Boolean.TRUE.equals(isIdempotent()) && idempotentRepository == null) {
            LOG.info("Using default memory based idempotent repository with cache max size: {}", DEFAULT_IDEMPOTENT_CACHE_SIZE);
            idempotentRepository = MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IDEMPOTENT_CACHE_SIZE);
        }

        if (!getConfiguration().isUseList() && getFileName() == null) {
            throw new IllegalArgumentException(
                    "Endpoint is configured with useList=false, then fileName must be configured also");
        }

        // set max messages per poll
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setEagerLimitMaxMessagesPerPoll(isEagerMaxMessagesPerPoll());

        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating GenericFilePollingConsumer with queueSize: {} blockWhenFull: {} blockTimeout: {}",
                    getPollingConsumerQueueSize(), isPollingConsumerBlockWhenFull(),
                    getPollingConsumerBlockTimeout());
        }
        GenericFilePollingConsumer result = new GenericFilePollingConsumer(this);
        // should not call configurePollingConsumer when its
        // GenericFilePollingConsumer
        result.setBlockWhenFull(isPollingConsumerBlockWhenFull());
        result.setBlockTimeout(getPollingConsumerBlockTimeout());

        return result;
    }

    /**
     * Validates this endpoint if its configured properly.
     *
     * @throws IllegalArgumentException is thrown if endpoint is invalid configured for its mandatory options
     */
    protected void afterPropertiesSet() {
        RemoteFileConfiguration config = getConfiguration();
        StringHelper.notEmpty(config.getHost(), "host");
        StringHelper.notEmpty(config.getProtocol(), "protocol");

        if (!isSynchronous()) {
            throw new IllegalArgumentException("Using synchronous=false is not supported for camel-ftp");
        }
    }

    @Override
    protected Map<String, Object> getParamsAsMap() {
        Map<String, Object> map = super.getParamsAsMap();
        map.put("fastExistsCheck", fastExistsCheck);
        return map;
    }

    /**
     * Remote File Endpoints, impl this method to create a custom consumer specific to their "protocol" etc.
     *
     * @param  processor the processor
     * @return           the created consumer
     */
    protected abstract RemoteFileConsumer<T> buildConsumer(Processor processor);

    /**
     * Remote File Endpoints, impl this method to create a custom producer specific to their "protocol" etc.
     *
     * @return the created producer
     */
    protected abstract GenericFileProducer<T> buildProducer();

    /**
     * Creates the operations to be used by the consumer or producer.
     *
     * @return           a new created operations
     * @throws Exception is thrown if error creating operations.
     */
    public abstract RemoteFileOperations<T> createRemoteFileOperations() throws Exception;

    /**
     * Returns human readable server information for logging purpose
     */
    public String remoteServerInformation() {
        return ((RemoteFileConfiguration) configuration).remoteServerInformation();
    }

    @Override
    public char getFileSeparator() {
        return '/';
    }

    @Override
    public boolean isAbsolute(String name) {
        return name.startsWith("/");
    }

    public int getMaximumReconnectAttempts() {
        return maximumReconnectAttempts;
    }

    /**
     * Specifies the maximum reconnect attempts Camel performs when it tries to connect to the remote FTP server. Use 0
     * to disable this behavior.
     */
    public void setMaximumReconnectAttempts(int maximumReconnectAttempts) {
        this.maximumReconnectAttempts = maximumReconnectAttempts;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    /**
     * Delay in millis Camel will wait before performing a reconnect attempt.
     */
    public void setReconnectDelay(long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    /**
     * Whether or not to disconnect from remote FTP server right after use. Disconnect will only disconnect the current
     * connection to the FTP server. If you have a consumer which you want to stop, then you need to stop the
     * consumer/route instead.
     */
    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isDisconnectOnBatchComplete() {
        return disconnectOnBatchComplete;
    }

    /**
     * Whether or not to disconnect from remote FTP server right after a Batch upload is complete.
     * disconnectOnBatchComplete will only disconnect the current connection to the FTP server.
     */
    public void setDisconnectOnBatchComplete(boolean disconnectOnBatchComplete) {
        this.disconnectOnBatchComplete = disconnectOnBatchComplete;
    }

    public boolean isFastExistsCheck() {
        return fastExistsCheck;
    }

    /**
     * If set this option to be true, camel-ftp will use the list file directly to check if the file exists. Since some
     * FTP server may not support to list the file directly, if the option is false, camel-ftp will use the old way to
     * list the directory and check if the file exists. This option also influences readLock=changed to control whether
     * it performs a fast check to update file information or not. This can be used to speed up the process if the FTP
     * server has a lot of files.
     */
    public void setFastExistsCheck(boolean fastExistsCheck) {
        this.fastExistsCheck = fastExistsCheck;
    }

    public boolean isDownload() {
        return this.download;
    }

    /**
     * Whether the FTP consumer should download the file. If this option is set to false, then the message body will be
     * null, but the consumer will still trigger a Camel Exchange that has details about the file such as file name,
     * file size, etc. It's just that the file will not be downloaded.
     */
    public void setDownload(boolean download) {
        this.download = download;
    }

}
