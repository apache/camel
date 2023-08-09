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
package org.apache.camel.component.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.component.file.strategy.FileProcessStrategyFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read and write files.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "file", title = "File", syntax = "file:directoryName",
             category = { Category.FILE, Category.CORE }, headersClass = FileConstants.class)
public class FileEndpoint extends GenericFileEndpoint<File> {

    private static final Logger LOG = LoggerFactory.getLogger(FileEndpoint.class);

    private static final Integer CHMOD_WRITE_MASK = 02;
    private static final Integer CHMOD_READ_MASK = 04;
    private static final Integer CHMOD_EXECUTE_MASK = 01;

    private final FileOperations operations = new FileOperations(this);

    @UriPath(name = "directoryName")
    @Metadata(required = true)
    private File file;
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean copyAndDeleteOnRenameFail = true;
    @UriParam(label = "advanced")
    private boolean renameUsingCopy;
    @UriParam(label = "consumer,advanced")
    private boolean includeHiddenFiles;
    @UriParam(label = "consumer,advanced")
    private boolean startingDirectoryMustExist;
    @UriParam(label = "consumer,advanced")
    private boolean startingDirectoryMustHaveAccess;
    @UriParam(label = "consumer,advanced")
    private boolean directoryMustExist;
    @UriParam(label = "consumer,advanced")
    private boolean probeContentType;
    @UriParam(label = "consumer,advanced")
    private String extendedAttributes;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean forceWrites = true;
    @UriParam(label = "producer,advanced")
    private String chmod;
    @UriParam(label = "producer,advanced")
    private String chmodDirectory;

    public FileEndpoint() {
    }

    public FileEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public FileConsumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(operations, "operations");
        ObjectHelper.notNull(file, "file");

        // auto create starting directory if needed
        if (!file.exists() && !file.isDirectory()) {
            if (isAutoCreate()) {
                LOG.debug("Creating non existing starting directory: {}", file);
                boolean absolute = FileUtil.isAbsolute(file);
                boolean created = operations.buildDirectory(file.getPath(), absolute);
                if (!created) {
                    LOG.warn("Cannot auto create starting directory: {}", file);
                }
            } else if (isStartingDirectoryMustExist()) {
                throw new FileNotFoundException("Starting directory does not exist: " + file);
            }
        }
        if (!isStartingDirectoryMustExist() && isStartingDirectoryMustHaveAccess()) {
            throw new IllegalArgumentException(
                    "You cannot set startingDirectoryMustHaveAccess=true without setting startingDirectoryMustExist=true");
        } else if (isStartingDirectoryMustExist() && isStartingDirectoryMustHaveAccess()) {
            if (!file.canRead() || !file.canWrite()) {
                throw new IOException("Starting directory permission denied: " + file);
            }
        }
        FileConsumer result = newFileConsumer(processor, operations);

        if (isDelete() && getMove() != null) {
            throw new IllegalArgumentException("You cannot set both delete=true and move options");
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

        if (ObjectHelper.isNotEmpty(getReadLock())) {
            // check if its a valid
            String valid = "none,markerFile,fileLock,rename,changed,idempotent,idempotent-changed,idempotent-rename";
            String[] arr = valid.split(",");
            boolean matched = Arrays.stream(arr).anyMatch(n -> n.equals(getReadLock()));
            if (!matched) {
                throw new IllegalArgumentException("ReadLock invalid: " + getReadLock() + ", must be one of: " + valid);
            }
        }

        // set max messages per poll
        result.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        result.setEagerLimitMaxMessagesPerPoll(isEagerMaxMessagesPerPoll());

        configureConsumer(result);
        return result;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        ObjectHelper.notNull(operations, "operations");
        ObjectHelper.notNull(file, "file");

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

    @Override
    public GenericFileProducer<File> createProducer() throws Exception {
        ObjectHelper.notNull(operations, "operations");

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
        if (this.getMoveExistingFileStrategy() == null) {
            this.setMoveExistingFileStrategy(createDefaultMoveExistingFileStrategy());
        }
        return new GenericFileProducer<>(this, operations);
    }

    @Override
    public Exchange createExchange(GenericFile<File> file) {
        Exchange exchange = createExchange();
        if (file != null) {
            file.bindToExchange(exchange, probeContentType);
        }
        return exchange;
    }

    /**
     * Strategy to create a new {@link FileConsumer}
     *
     * @param  processor  the given processor
     * @param  operations file operations
     * @return            the created consumer
     */
    protected FileConsumer newFileConsumer(Processor processor, GenericFileOperations<File> operations) {
        return new FileConsumer(
                this, processor, operations, processStrategy != null ? processStrategy : createGenericFileStrategy());
    }

    /**
     * Default Existing File Move Strategy
     *
     * @return the default implementation for file component
     */
    private FileMoveExistingStrategy createDefaultMoveExistingFileStrategy() {
        return new GenericFileDefaultMoveExistingFileStrategy();
    }

    @Override
    protected GenericFileProcessStrategy<File> createGenericFileStrategy() {
        return new FileProcessStrategyFactory().createGenericFileProcessStrategy(getCamelContext(), getParamsAsMap());
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

    @Override
    public boolean isHiddenFilesEnabled() {
        return includeHiddenFiles;
    }

    public boolean isCopyAndDeleteOnRenameFail() {
        return copyAndDeleteOnRenameFail;
    }

    /**
     * Whether to fallback and do a copy and delete file, in case the file could not be renamed directly. This option is
     * not available for the FTP component.
     */
    public void setCopyAndDeleteOnRenameFail(boolean copyAndDeleteOnRenameFail) {
        this.copyAndDeleteOnRenameFail = copyAndDeleteOnRenameFail;
    }

    public boolean isRenameUsingCopy() {
        return renameUsingCopy;
    }

    /**
     * Perform rename operations using a copy and delete strategy. This is primarily used in environments where the
     * regular rename operation is unreliable (e.g. across different file systems or networks). This option takes
     * precedence over the copyAndDeleteOnRenameFail parameter that will automatically fall back to the copy and delete
     * strategy, but only after additional delays.
     */
    public void setRenameUsingCopy(boolean renameUsingCopy) {
        this.renameUsingCopy = renameUsingCopy;
    }

    public boolean isIncludeHiddenFiles() {
        return includeHiddenFiles;
    }

    /**
     * Whether to accept hidden files. Files which names starts with dot is regarded as a hidden file, and by default
     * not included. Set this option to true to include hidden files in the file consumer.
     */
    public void setIncludeHiddenFiles(boolean includeHiddenFiles) {
        this.includeHiddenFiles = includeHiddenFiles;
    }

    public boolean isStartingDirectoryMustExist() {
        return startingDirectoryMustExist;
    }

    /**
     * Whether the starting directory must exist. Mind that the autoCreate option is default enabled, which means the
     * starting directory is normally auto created if it doesn't exist. You can disable autoCreate and enable this to
     * ensure the starting directory must exist. Will thrown an exception if the directory doesn't exist.
     */
    public void setStartingDirectoryMustExist(boolean startingDirectoryMustExist) {
        this.startingDirectoryMustExist = startingDirectoryMustExist;
    }

    public boolean isStartingDirectoryMustHaveAccess() {
        return startingDirectoryMustHaveAccess;
    }

    /**
     * Whether the starting directory has access permissions. Mind that the startingDirectoryMustExist parameter must be
     * set to true in order to verify that the directory exists. Will thrown an exception if the directory doesn't have
     * read and write permissions.
     */
    public void setStartingDirectoryMustHaveAccess(boolean startingDirectoryMustHaveAccess) {
        this.startingDirectoryMustHaveAccess = startingDirectoryMustHaveAccess;
    }

    public boolean isDirectoryMustExist() {
        return directoryMustExist;
    }

    /**
     * Similar to the startingDirectoryMustExist option but this applies during polling (after starting the consumer).
     */
    public void setDirectoryMustExist(boolean directoryMustExist) {
        this.directoryMustExist = directoryMustExist;
    }

    public boolean isForceWrites() {
        return forceWrites;
    }

    /**
     * Whether to force syncing writes to the file system. You can turn this off if you do not want this level of
     * guarantee, for example if writing to logs / audit logs etc; this would yield better performance.
     */
    public void setForceWrites(boolean forceWrites) {
        this.forceWrites = forceWrites;
    }

    public boolean isProbeContentType() {
        return probeContentType;
    }

    /**
     * Whether to enable probing of the content type. If enable then the consumer uses
     * {@link Files#probeContentType(java.nio.file.Path)} to determine the content-type of the file, and store that as a
     * header with key {@link Exchange#FILE_CONTENT_TYPE} on the {@link Message}.
     */
    public void setProbeContentType(boolean probeContentType) {
        this.probeContentType = probeContentType;
    }

    public String getExtendedAttributes() {
        return extendedAttributes;
    }

    /**
     * To define which file attributes of interest. Like posix:permissions,posix:owner,basic:lastAccessTime, it supports
     * basic wildcard like posix:*, basic:lastAccessTime
     */
    public void setExtendedAttributes(String extendedAttributes) {
        this.extendedAttributes = extendedAttributes;
    }

    /**
     * Chmod value must be between 000 and 777; If there is a leading digit like in 0755 we will ignore it.
     */
    public boolean chmodPermissionsAreValid(String chmod) {
        if (chmod == null || chmod.length() < 3 || chmod.length() > 4) {
            return false;
        }
        // if 4 digits chop off leading one
        String permissionsString = chmod.trim().substring(chmod.length() - 3);
        for (int i = 0; i < permissionsString.length(); i++) {
            char c = permissionsString.charAt(i);
            if (!Character.isDigit(c) || Integer.parseInt(Character.toString(c)) > 7) {
                return false;
            }
        }
        return true;
    }

    public Set<PosixFilePermission> getPermissions() {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if (ObjectHelper.isEmpty(chmod)) {
            return permissions;
        }

        String chmodString = chmod.substring(chmod.length() - 3); // if 4 digits
                                                                 // chop off
                                                                 // leading one

        int ownerValue = Integer.parseInt(chmodString.substring(0, 1));
        int groupValue = Integer.parseInt(chmodString.substring(1, 2));
        int othersValue = Integer.parseInt(chmodString.substring(2, 3));

        if ((ownerValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((ownerValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_READ);
        }
        if ((ownerValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }

        if ((groupValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((groupValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_READ);
        }
        if ((groupValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        }

        if ((othersValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((othersValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_READ);
        }
        if ((othersValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }

        return permissions;
    }

    public String getChmod() {
        return chmod;
    }

    /**
     * Specify the file permissions which is sent by the producer, the chmod value must be between 000 and 777; If there
     * is a leading digit like in 0755 we will ignore it.
     */
    public void setChmod(String chmod) {
        if (ObjectHelper.isNotEmpty(chmod) && chmodPermissionsAreValid(chmod)) {
            this.chmod = chmod.trim();
        } else {
            throw new IllegalArgumentException("chmod option [" + chmod + "] is not valid");
        }
    }

    public Set<PosixFilePermission> getDirectoryPermissions() {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if (ObjectHelper.isEmpty(chmodDirectory)) {
            return permissions;
        }

        // if 4 digits chop off leading one
        String chmodString = chmodDirectory.substring(chmodDirectory.length() - 3);

        int ownerValue = Integer.parseInt(chmodString.substring(0, 1));
        int groupValue = Integer.parseInt(chmodString.substring(1, 2));
        int othersValue = Integer.parseInt(chmodString.substring(2, 3));

        if ((ownerValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((ownerValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_READ);
        }
        if ((ownerValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }

        if ((groupValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((groupValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_READ);
        }
        if ((groupValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        }

        if ((othersValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((othersValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_READ);
        }
        if ((othersValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }

        return permissions;
    }

    public String getChmodDirectory() {
        return chmodDirectory;
    }

    /**
     * Specify the directory permissions used when the producer creates missing directories, the chmod value must be
     * between 000 and 777; If there is a leading digit like in 0755 we will ignore it.
     */
    public void setChmodDirectory(String chmodDirectory) {
        if (ObjectHelper.isNotEmpty(chmodDirectory) && chmodPermissionsAreValid(chmodDirectory)) {
            this.chmodDirectory = chmodDirectory.trim();
        } else {
            throw new IllegalArgumentException("chmodDirectory option [" + chmodDirectory + "] is not valid");
        }
    }

}
