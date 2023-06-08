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

import java.net.URI;
import java.time.Duration;

import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.ShareServiceClientBuilder;
import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.Category;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.component.file.azure.strategy.FilesProcessStrategyFactory;
import org.apache.camel.component.file.remote.RemoteFileComponent;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// , extendsScheme = "file"   in FTPS but AzureBlob does not have it
@UriEndpoint(firstVersion = "3.21.0", scheme = "azure-files", extendsScheme = "file", title = "Azure Files",
             syntax = "azure-files://host/share", category = {
                     Category.CLOUD, Category.FILE },
             headersClass = FilesHeaders.class)
@Metadata(excludeProperties = "appendChars,readLockIdempotentReleaseAsync,readLockIdempotentReleaseAsyncPoolSize,"
                              + "readLockIdempotentReleaseDelay,readLockIdempotentReleaseExecutorService,"
                              + "directoryMustExist,extendedAttributes,probeContentType,startingDirectoryMustExist,"
                              + "startingDirectoryMustHaveAccess,chmodDirectory,forceWrites,copyAndDeleteOnRenameFail,"
                              + "renameUsingCopy,synchronous,passive,passiveMode,stepwise,useList,binary,charset,password,siteCommand,fastExistsCheck,soTimeout,separator")
@ManagedResource(description = "Managed Azure Files Endpoint")
public class FilesEndpoint<T extends ShareFileItem> extends RemoteFileEndpoint<ShareFileItem> {

    private static final Logger LOG = LoggerFactory.getLogger(FilesEndpoint.class);

    @UriParam
    protected FilesConfiguration configuration;

    @UriParam(label = "consumer")
    protected boolean resumeDownload;

    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String sv;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String ss;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String srt;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String sp;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String se;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String st;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String spr;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String sig;

    private Token token = new Token();

    public FilesEndpoint() {
    }

    public FilesEndpoint(String uri, RemoteFileComponent<ShareFileItem> component,
                         FilesConfiguration configuration) {
        super(uri, component, configuration);
        this.configuration = configuration;
    }

    public String getSv() {
        return sv;
    }

    public void setSv(String sv) {
        token.setSv(sv);
        this.sv = sv;
    }

    public String getSs() {
        return ss;
    }

    public void setSs(String ss) {
        token.setSs(ss);
        this.ss = ss;
    }

    public String getSrt() {
        return srt;
    }

    public void setSrt(String srt) {
        token.setSrt(srt);
        this.srt = srt;
    }

    public String getSp() {
        return sp;
    }

    public void setSp(String sp) {
        token.setSp(sp);
        this.sp = sp;
    }

    public String getSe() {
        return se;
    }

    public void setSe(String se) {
        token.setSe(se);
        this.se = se;
    }

    public String getSt() {
        return st;
    }

    public void setSt(String st) {
        token.setSt(st);
        this.st = st;
    }

    public String getSpr() {
        return spr;
    }

    public void setSpr(String spr) {
        token.setSpr(spr);
        this.spr = spr;
    }

    public String getSig() {
        return sig;
    }

    public void setSig(String sig) {
        token.setSig(sig);
        this.sig = sig;
    }

    @Override
    public String getScheme() {
        return "azure-files";
    }

    @Override
    public RemoteFileConsumer<ShareFileItem> createConsumer(Processor processor) throws Exception {
        if (isResumeDownload() && ObjectHelper.isEmpty(getLocalWorkDirectory())) {
            throw new IllegalArgumentException(
                    "The option localWorkDirectory must be configured when resumeDownload=true");
        }
        if (isResumeDownload() && !getConfiguration().isBinary()) {
            throw new IllegalArgumentException(
                    "The option binary must be enabled when resumeDownload=true");
        }
        return super.createConsumer(processor);
    }

    public String getShare() {
        var base = getEndpointBaseUri();
        var path = URI.create(base).getPath();
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            return null;
        }
        var share = path.substring(1);
        if (share.contains("/")) {
            share = share.substring(0, share.indexOf('/'));
        }
        return share;
    }

    @Override
    protected RemoteFileConsumer<ShareFileItem> buildConsumer(Processor processor) {
        try {
            return new FilesConsumer(
                    this, processor, createRemoteFileOperations(),
                    processStrategy != null ? processStrategy : createGenericFileStrategy());
        } catch (Exception e) {
            throw new FailedToCreateConsumerException(this, e);
        }
    }

    @Override
    protected GenericFileProducer<ShareFileItem> buildProducer() {
        try {
            if (this.getMoveExistingFileStrategy() == null) {
                this.setMoveExistingFileStrategy(createDefaultFtpMoveExistingFileStrategy());
            }
            return new FilesProducer<>(this, createRemoteFileOperations());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(this, e);
        }
    }

    private FileMoveExistingStrategy createDefaultFtpMoveExistingFileStrategy() {
        return new FileMoveExistingStrategy() {
            @Override
            public boolean moveExistingFile(
                    GenericFileEndpoint endpoint,
                    GenericFileOperations operations, String fileName)
                    throws GenericFileOperationFailedException {
                LOG.warn("The fileExist=Move option is not supported.");
                return false;
            }
        };
    }

    @Override
    protected GenericFileProcessStrategy<ShareFileItem> createGenericFileStrategy() {
        return new FilesProcessStrategyFactory().createGenericFileProcessStrategy(getCamelContext(),
                getParamsAsMap());
    }

    @Override
    public RemoteFileOperations<ShareFileItem> createRemoteFileOperations() throws Exception {
        ShareServiceClient client = createClient();

        FilesOperations operations = new FilesOperations(client);
        operations.setEndpoint(this);
        return operations;
    }

    /**
     * Create the client
     *
     * @throws Exception may throw client-specific exceptions if the client cannot be created
     */
    protected ShareServiceClient createClient() throws Exception {
        // TODO take from signed protocol? it would be token only
        ShareServiceClient client = new ShareServiceClientBuilder().endpoint("https://" + filesHost())
                .sasToken(token().toURIQuery()).buildClient();
        return client;
    }

    Token token() {
        return token;
    }

    String filesHost() {
        var base = getEndpointBaseUri();
        var schemeAuthSeparator = base.indexOf("://");
        if (schemeAuthSeparator == -1) {
            return null;
        }
        var from = schemeAuthSeparator + 3;
        return base.substring(from, base.indexOf('/', from));
    }

    @Override
    public FilesConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new FilesConfiguration();
        }
        return configuration;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration expected");
        }
        // need to set on both
        this.configuration = (FilesConfiguration) configuration;
        super.setConfiguration(configuration);
    }

    public boolean isResumeDownload() {
        return resumeDownload;
    }

    /**
     * Configures whether resume download is enabled. This must be supported by the FTP server (almost all FTP servers
     * support it). In addition the options <tt>localWorkDirectory</tt> must be configured so downloaded files are
     * stored in a local directory, and the option <tt>binary</tt> must be enabled, which is required to support
     * resuming of downloads.
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
        var t1 = configuration.getTimeout();
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
        var t1 = configuration.getTimeout();
        if (t1 > 0) {
            return Duration.ofMillis(t1);
        }
        return Duration.ofDays(10); // block
    }

}
