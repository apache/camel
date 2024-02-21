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
package org.apache.camel.component.file.azure;

import java.time.Duration;

import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.Category;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.azure.strategy.FilesProcessStrategyFactory;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * Send and receive files to Azure storage file share
 */
@UriEndpoint(firstVersion = "3.22.0", scheme = FilesComponent.SCHEME, extendsScheme = "file", title = "Azure Files",
             syntax = FilesComponent.SCHEME + ":account/share", category = {
                     Category.CLOUD, Category.FILE },
             headersClass = FilesHeaders.class)
@Metadata(excludeProperties = "appendChars,readLockIdempotentReleaseAsync,readLockIdempotentReleaseAsyncPoolSize,"
                              + "readLockIdempotentReleaseDelay,readLockIdempotentReleaseExecutorService,"
                              + "directoryMustExist,extendedAttributes,probeContentType,startingDirectoryMustExist,"
                              + "startingDirectoryMustHaveAccess,chmodDirectory,forceWrites,copyAndDeleteOnRenameFail,"
                              + "renameUsingCopy,synchronous,passive,passiveMode,stepwise,useList,binary,charset,password,"
                              + "siteCommand,fastExistsCheck,soTimeout,separator,sendNoop,ignoreFileNotFoundOrPermissionError,"
                              + "bufferSize,moveExisting,username,host")
@ManagedResource(description = "Camel Azure Files endpoint")
public class FilesEndpoint extends RemoteFileEndpoint<ShareFileItem> {

    // without hiding configuration field from type GenericFileEndpoint<ShareFileItem>
    // camel-package-maven-plugin: Missing @UriPath on endpoint
    @UriParam
    protected FilesConfiguration configuration;
    @UriParam
    protected FilesToken token = new FilesToken();
    @UriParam(label = "consumer")
    protected boolean resumeDownload;

    public FilesEndpoint() {
    }

    public FilesEndpoint(String uri, FilesComponent component,
                         FilesConfiguration configuration) {
        super(uri, component, configuration);
        setConfiguration(configuration);
    }

    @Override
    public String getScheme() {
        return FilesComponent.SCHEME;
    }

    @Override
    public RemoteFileConsumer<ShareFileItem> createConsumer(Processor processor) throws Exception {
        if (isResumeDownload() && ObjectHelper.isEmpty(getLocalWorkDirectory())) {
            throw new IllegalArgumentException(
                    "The option localWorkDirectory must be configured when resumeDownload=true");
        }
        return super.createConsumer(processor);
    }

    @Override
    protected FilesConsumer buildConsumer(Processor processor) {
        try {
            return new FilesConsumer(
                    this, processor, createRemoteFileOperations(),
                    processStrategy != null ? processStrategy : createGenericFileStrategy());
        } catch (Exception e) {
            throw new FailedToCreateConsumerException(this, e);
        }
    }

    @Override
    protected FilesProducer buildProducer() {
        try {
            return new FilesProducer(this, createRemoteFileOperations());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(this, e);
        }
    }

    @Override
    protected GenericFileProcessStrategy<ShareFileItem> createGenericFileStrategy() {
        return new FilesProcessStrategyFactory().createGenericFileProcessStrategy(getCamelContext(),
                getParamsAsMap());
    }

    @Override
    public FilesOperations createRemoteFileOperations() {
        return new FilesOperations(this);
    }

    public FilesToken getToken() {
        return token;
    }

    public void setToken(FilesToken token) {
        this.token = token;
    }

    @Override
    public FilesConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        if (!(configuration instanceof FilesConfiguration)) {
            throw new IllegalArgumentException("FilesConfiguration expected.");
        }
        super.setConfiguration(configuration);
        this.configuration = (FilesConfiguration) configuration;
    }

    public boolean isResumeDownload() {
        return resumeDownload;
    }

    /**
     * Configures whether resume download is enabled. In addition the options <tt>localWorkDirectory</tt> must be
     * configured so downloaded files are stored in a local directory, which is required to support resuming of
     * downloads.
     */
    public void setResumeDownload(boolean resumeDownload) {
        this.resumeDownload = resumeDownload;
    }

    @Override
    public char getFileSeparator() {
        return FilesPath.PATH_SEPARATOR;
    }

    @Override
    public String getCharset() {
        // unlike FTP, always binary
        return null;
    }

    Duration getMetadataTimeout() {
        var t1 = getConfiguration().getTimeout();
        var t2 = getReadLockCheckInterval();
        if (t2 > 0 && t2 < t1) {
            return Duration.ofMillis(t2);
        }
        if (t1 > 0) {
            return Duration.ofMillis(t1);
        }
        return Duration.ofSeconds(20);
    }

    Duration getDataTimeout() {
        var t1 = getConfiguration().getTimeout();
        if (t1 > 0) {
            return Duration.ofMillis(t1);
        }
        return Duration.ofDays(10); // block
    }

}
