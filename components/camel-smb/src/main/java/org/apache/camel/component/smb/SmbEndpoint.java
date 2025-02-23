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
package org.apache.camel.component.smb;

import java.util.Map;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileConsumer;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFilePollingConsumer;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.component.smb.strategy.SmbProcessStrategyFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read and write files to Server Message Block (SMB) file shares.
 */
@UriEndpoint(firstVersion = "4.3.0", scheme = "smb", title = "SMB", syntax = "smb:hostname:port/shareName",
             headersClass = SmbConstants.class, category = { Category.FILE })
@Metadata(excludeProperties = "appendChars,readLockIdempotentReleaseAsync,readLockIdempotentReleaseAsyncPoolSize,"
                              + "readLockIdempotentReleaseDelay,readLockIdempotentReleaseExecutorService,"
                              + "directoryMustExist,extendedAttributes,probeContentType,"
                              + "startingDirectoryMustHaveAccess,chmodDirectory,forceWrites,copyAndDeleteOnRenameFail,"
                              + "renameUsingCopy,synchronous")
public class SmbEndpoint extends GenericFileEndpoint<FileIdBothDirectoryInformation> implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(SmbEndpoint.class);

    @UriParam
    private SmbConfiguration configuration;

    protected SmbEndpoint(String uri, SmbComponent component, SmbConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public boolean isSingletonProducer() {
        // this producer is stateful because the smb file operations is not
        // thread safe
        return false;
    }

    @Override
    public String getScheme() {
        return "smb";
    }

    @Override
    public char getFileSeparator() {
        return '/';
    }

    @Override
    public boolean isAbsolute(String name) {
        return name.startsWith("/");
    }

    @Override
    protected GenericFileProcessStrategy<FileIdBothDirectoryInformation> createGenericFileStrategy() {
        return new SmbProcessStrategyFactory().createGenericFileProcessStrategy(getCamelContext(), getParamsAsMap());
    }

    @Override
    public GenericFileProducer<FileIdBothDirectoryInformation> createProducer() throws Exception {
        try {
            if (this.getMoveExistingFileStrategy() == null) {
                this.setMoveExistingFileStrategy(createDefaultFtpMoveExistingFileStrategy());
            }
            return new SmbProducer(this, createOperations());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(this, e);
        }
    }

    @Override
    public String getServiceUrl() {
        return configuration.getProtocol() + ":" + configuration.getHostname() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return configuration.getProtocol();
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getUsername() != null) {
            return Map.of("username", configuration.getUsername());
        }
        return null;
    }

    @Override
    public SmbConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Default Existing File Move Strategy
     *
     * @return the default implementation for SMB components
     */
    private FileMoveExistingStrategy createDefaultFtpMoveExistingFileStrategy() {
        return new SmbDefaultMoveExistingFileStrategy();
    }

    @Override
    public GenericFileConsumer<FileIdBothDirectoryInformation> createConsumer(Processor processor) throws Exception {
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

        SmbConsumer consumer = new SmbConsumer(
                this, processor, createOperations(),
                processStrategy != null ? processStrategy : createGenericFileStrategy());
        consumer.setMaxMessagesPerPoll(this.getMaxMessagesPerPoll());
        consumer.setEagerLimitMaxMessagesPerPoll(this.isEagerMaxMessagesPerPoll());
        return consumer;
    }

    public GenericFileOperations<FileIdBothDirectoryInformation> createOperations() {
        SmbOperations operations = new SmbOperations(configuration);
        operations.setEndpoint(this);
        return operations;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating GenericFilePollingConsumer with queueSize: {} blockWhenFull: {} blockTimeout: {}",
                    getPollingConsumerQueueSize(), isPollingConsumerBlockWhenFull(),
                    getPollingConsumerBlockTimeout());
        }
        GenericFilePollingConsumer result = new GenericFilePollingConsumer(this);
        result.setBlockWhenFull(isPollingConsumerBlockWhenFull());
        result.setBlockTimeout(getPollingConsumerBlockTimeout());
        return result;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("SmbConfiguration expected");
        }
        this.configuration = (SmbConfiguration) configuration;
        super.setConfiguration(configuration);
    }

    @Override
    public Exchange createExchange(GenericFile<FileIdBothDirectoryInformation> file) {
        Exchange answer = super.createExchange();
        if (file != null) {
            file.bindToExchange(answer);
        }
        return answer;
    }
}
