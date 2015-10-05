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
package org.apache.camel.component.file;

import java.io.File;
import java.io.FileNotFoundException;

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

/**
 * File endpoint.
 */
@UriEndpoint(scheme = "file", title = "File", syntax = "file:directoryName", consumerClass = FileConsumer.class, label = "core,file")
public class FileEndpoint extends GenericFileEndpoint<File> {

    private final FileOperations operations = new FileOperations(this);

    @UriPath(name = "directoryName") @Metadata(required = "true")
    private File file;
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean copyAndDeleteOnRenameFail = true;
    @UriParam(label = "advanced")
    private boolean renameUsingCopy;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean forceWrites = true;

    public FileEndpoint() {
        // use marker file as default exclusive read locks
        this.readLock = "markerFile";
    }

    public FileEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        // use marker file as default exclusive read locks
        this.readLock = "markerFile";
    }

    public FileConsumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(operations, "operations");
        ObjectHelper.notNull(file, "file");

        // auto create starting directory if needed
        if (!file.exists() && !file.isDirectory()) {
            if (isAutoCreate()) {
                log.debug("Creating non existing starting directory: {}", file);
                boolean absolute = FileUtil.isAbsolute(file);
                boolean created = operations.buildDirectory(file.getPath(), absolute);
                if (!created) {
                    log.warn("Cannot auto create starting directory: {}", file);
                }
            } else if (isStartingDirectoryMustExist()) {
                throw new FileNotFoundException("Starting directory does not exist: " + file);
            }
        }

        FileConsumer result = newFileConsumer(processor, operations);

        if (isDelete() && getMove() != null) {
            throw new IllegalArgumentException("You cannot set both delete=true and move options");
        }

        // if noop=true then idempotent should also be configured
        if (isNoop() && !isIdempotentSet()) {
            log.info("Endpoint is configured with noop=true so forcing endpoint to be idempotent as well");
            setIdempotent(true);
        }

        // if idempotent and no repository set then create a default one
        if (isIdempotentSet() && isIdempotent() && idempotentRepository == null) {
            log.info("Using default memory based idempotent repository with cache max size: " + DEFAULT_IDEMPOTENT_CACHE_SIZE);
            idempotentRepository = MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IDEMPOTENT_CACHE_SIZE);
        }

        // set max messages per poll
        result.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        result.setEagerLimitMaxMessagesPerPoll(isEagerMaxMessagesPerPoll());

        configureConsumer(result);
        return result;
    }

    public GenericFileProducer<File> createProducer() throws Exception {
        ObjectHelper.notNull(operations, "operations");

        // you cannot use temp file and file exists append
        if (getFileExist() == GenericFileExist.Append && ((getTempPrefix() != null) || (getTempFileName() != null))) {
            throw new IllegalArgumentException("You cannot set both fileExist=Append and tempPrefix/tempFileName options");
        }

        // ensure fileExist and moveExisting is configured correctly if in use
        if (getFileExist() == GenericFileExist.Move && getMoveExisting() == null) {
            throw new IllegalArgumentException("You must configure moveExisting option when fileExist=Move");
        } else if (getMoveExisting() != null && getFileExist() != GenericFileExist.Move) {
            throw new IllegalArgumentException("You must configure fileExist=Move when moveExisting has been set");
        }

        return new GenericFileProducer<File>(this, operations);
    }

    public Exchange createExchange(GenericFile<File> file) {
        Exchange exchange = createExchange();
        if (file != null) {
            file.bindToExchange(exchange);
        }
        return exchange;
    }

    /**
     * Strategy to create a new {@link FileConsumer}
     *
     * @param processor  the given processor
     * @param operations file operations
     * @return the created consumer
     */
    protected FileConsumer newFileConsumer(Processor processor, GenericFileOperations<File> operations) {
        return new FileConsumer(this, processor, operations);
    }

    public File getFile() {
        return file;
    }

    /**
     * The starting directory
     */
    public void setFile(File file) {
        this.file = file;
        // update configuration as well
        getConfiguration().setDirectory(FileUtil.isAbsolute(file) ? file.getAbsolutePath() : file.getPath());
    }

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    protected String createEndpointUri() {
        return getFile().toURI().toString();
    }

    @Override
    public char getFileSeparator() {       
        return File.separatorChar;
    }

    @Override
    public boolean isAbsolute(String name) {
        // relative or absolute path?
        return FileUtil.isAbsolute(new File(name));
    }

    public boolean isCopyAndDeleteOnRenameFail() {
        return copyAndDeleteOnRenameFail;
    }

    /**
     * Whether to fallback and do a copy and delete file, in case the file could not be renamed directly. This option is not available for the FTP component.
     */
    public void setCopyAndDeleteOnRenameFail(boolean copyAndDeleteOnRenameFail) {
        this.copyAndDeleteOnRenameFail = copyAndDeleteOnRenameFail;
    }

    public boolean isRenameUsingCopy() {
        return renameUsingCopy;
    }

    /**
     * Perform rename operations using a copy and delete strategy.
     * This is primarily used in environments where the regular rename operation is unreliable (e.g. across different file systems or networks).
     * This option takes precedence over the copyAndDeleteOnRenameFail parameter that will automatically fall back to the copy and delete strategy,
     * but only after additional delays.
     */
    public void setRenameUsingCopy(boolean renameUsingCopy) {
        this.renameUsingCopy = renameUsingCopy;
    }

    public boolean isForceWrites() {
        return forceWrites;
    }

    /**
     * Whether to force syncing writes to the file system.
     * You can turn this off if you do not want this level of guarantee, for example if writing to logs / audit logs etc; this would yield better performance.
     */
    public void setForceWrites(boolean forceWrites) {
        this.forceWrites = forceWrites;
    }
}
