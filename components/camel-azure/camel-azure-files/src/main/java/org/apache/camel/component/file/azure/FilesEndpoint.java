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

import com.azure.storage.common.StorageSharedKeyCredential;
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
import org.apache.camel.component.file.azure.strategy.FilesProcessStrategyFactory;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// , extendsScheme = "file"   in FTPS but AzureBlob does not have it
@UriEndpoint(firstVersion = "3.21.0", scheme = FilesComponent.SCHEME, extendsScheme = "file", title = "Azure Files",
             syntax = FilesComponent.SCHEME + "://account[.host]/share[/dir]", category = {
                     Category.CLOUD, Category.FILE },
             headersClass = FilesHeaders.class)
@Metadata(excludeProperties = "appendChars,readLockIdempotentReleaseAsync,readLockIdempotentReleaseAsyncPoolSize,"
                              + "readLockIdempotentReleaseDelay,readLockIdempotentReleaseExecutorService,"
                              + "directoryMustExist,extendedAttributes,probeContentType,startingDirectoryMustExist,"
                              + "startingDirectoryMustHaveAccess,chmodDirectory,forceWrites,copyAndDeleteOnRenameFail,"
                              + "renameUsingCopy,synchronous,passive,passiveMode,stepwise,useList,binary,charset,password,"
                              + "siteCommand,fastExistsCheck,soTimeout,separator,sendNoop,ignoreFileNotFoundOrPermissionError,"
                              + "bufferSize,moveExisting,username")
@ManagedResource(description = "Camel Azure Files Endpoint")
public class FilesEndpoint extends RemoteFileEndpoint<ShareFileItem> {

    public static final String HTTPS = "https";

    private static final Logger LOG = LoggerFactory.getLogger(FilesEndpoint.class);

    // without hiding configuration field from type GenericFileEndpoint<ShareFileItem>
    // camel-package-maven-plugin: Missing @UriPath on endpoint 
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

    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String si;  // service SAS only
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String sr;  // service SAS only
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String sdd; // service SAS only
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String sip;

    @UriParam(label = "both", description = "Shared key (storage account key)", secret = true)
    private String sharedKey;

    private FilesToken token = new FilesToken();

    public FilesEndpoint() {
    }

    public FilesEndpoint(String uri, FilesComponent component,
                         FilesConfiguration configuration) {
        super(uri, component, configuration);
        setConfiguration(configuration);
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

    public String getSi() {
        return si;
    }

    public void setSi(String si) {
        token.setSi(si);
        this.si = si;
    }

    public String getSr() {
        return sr;
    }

    public void setSr(String sr) {
        token.setSr(sr);
        this.sr = sr;
    }

    public String getSdd() {
        return sdd;
    }

    public void setSdd(String sdd) {
        token.setSdd(sdd);
        this.sdd = sdd;
    }

    public String getSip() {
        return sip;
    }

    public void setSip(String sip) {
        token.setSip(sip);
        this.sip = sip;
    }

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
    }

    @Override
    public String getScheme() {
        // TODO or name of component bean?
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

    String getShare() {
        return getConfiguration().getShare();
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
            if (this.getMoveExistingFileStrategy() == null) {
                this.setMoveExistingFileStrategy(createDoNotMoveExistingFileStrategy());
            }
            return new FilesProducer(this, createRemoteFileOperations());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(this, e);
        }
    }

    private FileMoveExistingStrategy createDoNotMoveExistingFileStrategy() {
        return new FileMoveExistingStrategy() {
            @Override
            public boolean moveExistingFile(
                    GenericFileEndpoint endpoint,
                    GenericFileOperations operations, String fileName)
                    throws GenericFileOperationFailedException {
                LOG.warn("The fileExist=Move option is not implemented.");
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
    public FilesOperations createRemoteFileOperations() throws Exception {
        ShareServiceClient client = createClient();

        FilesOperations operations = new FilesOperations(client);
        operations.setEndpoint(this);
        return operations;
    }

    /**
     * Create the Azure Files service client
     *
     * @throws Exception may throw client-specific exceptions if the client cannot be created
     */
    protected ShareServiceClient createClient() throws Exception {
        var builder = new ShareServiceClientBuilder().endpoint(HTTPS + "://" + getConfiguration().getHost());
        if (token.isInvalid()) {
            if (sharedKey != null) {
                LOG.warn("The configured SAS token is not valid, using the shared key fallback.");
                var keyB64 = FilesURIStrings.reconstructBase64EncodedValue(sharedKey);
                builder.credential(new StorageSharedKeyCredential(getConfiguration().getAccount(), keyB64));
            } else {
                LOG.error("A valid SAS token or shared key must be configured.");
            }
            // TODO Azure AD https://learn.microsoft.com/en-us/rest/api/storageservices/authorize-requests-to-azure-storage
        } else {
            builder = builder.sasToken(token().toURIQuery());
        }
        return builder.buildClient();
    }

    FilesToken token() {
        return token;
    }

    @Override
    public FilesConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        if (configuration == null || !(configuration instanceof FilesConfiguration)) {
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
