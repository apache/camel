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
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic file producer
 */
public class GenericFileProducer<T> extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(GenericFileProducer.class);

    protected final GenericFileEndpoint<T> endpoint;
    protected GenericFileOperations<T> operations;
    // assume writing to 100 different files concurrently at most for the same
    // file producer
    private final Map<String, Lock> locks = Collections.synchronizedMap(LRUCacheFactory.newLRUCache(100));

    protected GenericFileProducer(GenericFileEndpoint<T> endpoint, GenericFileOperations<T> operations) {
        super(endpoint);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    public String getFileSeparator() {
        return File.separator;
    }

    public String normalizePath(String name) {
        return FileUtil.normalizePath(name);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // store any existing file header which we want to keep and propagate
        final String existing = exchange.getIn().getHeader(FileConstants.FILE_NAME, String.class);

        // create the target file name
        String target = createFileName(exchange);

        // use lock for same file name to avoid concurrent writes to the same
        // file
        // for example when you concurrently append to the same file
        Lock lock = locks.computeIfAbsent(target, f -> new ReentrantLock());

        lock.lock();
        try {
            processExchange(exchange, target);
        } finally {
            // do not remove as the locks cache has an upper bound
            // this ensure the locks is appropriate reused
            lock.unlock();
            // and remove the write file name header as we only want to use it
            // once (by design)
            exchange.getIn().removeHeader(Exchange.OVERRULE_FILE_NAME);
            // and restore existing file name
            exchange.getIn().setHeader(FileConstants.FILE_NAME, existing);
        }
    }

    /**
     * Sets the operations to be used.
     * <p/>
     * Can be used to set a fresh operations in case of recovery attempts
     *
     * @param operations the operations
     */
    public void setOperations(GenericFileOperations<T> operations) {
        this.operations = operations;
    }

    /**
     * Perform the work to process the fileExchange
     *
     * @param  exchange  fileExchange
     * @param  target    the target filename
     * @throws Exception is thrown if some error
     */
    protected void processExchange(Exchange exchange, String target) throws Exception {
        LOG.trace("Processing file: {} for exchange: {}", target, exchange);

        try {
            preWriteCheck(exchange);

            // should we write to a temporary name and then afterwards rename to
            // real target
            boolean writeAsTempAndRename = ObjectHelper.isNotEmpty(endpoint.getTempFileName());
            String tempTarget = null;
            // remember if target exists to avoid checking twice
            boolean targetExists;
            if (writeAsTempAndRename) {
                // compute temporary name with the temp prefix
                tempTarget = createTempFileName(exchange, target);

                LOG.trace("Writing using tempNameFile: {}", tempTarget);

                // if we should eager delete target file before deploying
                // temporary file
                if (endpoint.getFileExist() != GenericFileExist.TryRename && endpoint.isEagerDeleteTargetFile()) {

                    // cater for file exists option on the real target as
                    // the file operations code will work on the temp file

                    // if an existing file already exists what should we do?
                    targetExists = operations.existsFile(target);
                    if (targetExists) {

                        LOG.trace("EagerDeleteTargetFile, target exists");

                        if (endpoint.getFileExist() == GenericFileExist.Ignore) {
                            // ignore but indicate that the file was written
                            LOG.trace("An existing file already exists: {}. Ignore and do not override it.", target);
                            return;
                        } else if (endpoint.getFileExist() == GenericFileExist.Fail) {
                            throw new GenericFileOperationFailedException(
                                    "File already exist: " + target + ". Cannot write new file.");
                        } else if (endpoint.getFileExist() == GenericFileExist.Move) {
                            // move any existing file first
                            this.endpoint.getMoveExistingFileStrategy().moveExistingFile(endpoint, operations, target);
                        } else if (endpoint.isEagerDeleteTargetFile() && endpoint.getFileExist() == GenericFileExist.Override) {
                            // we override the target so we do this by deleting
                            // it so the temp file can be renamed later
                            // with success as the existing target file have
                            // been deleted
                            LOG.trace("Eagerly deleting existing file: {}", target);
                            if (!operations.deleteFile(target)) {
                                throw new GenericFileOperationFailedException("Cannot delete file: " + target);
                            }
                        }
                    }
                }

                // delete any pre existing temp file
                if (endpoint.getFileExist() != GenericFileExist.TryRename && operations.existsFile(tempTarget)) {
                    LOG.trace("Deleting existing temp file: {}", tempTarget);
                    if (!operations.deleteFile(tempTarget)) {
                        throw new GenericFileOperationFailedException("Cannot delete file: " + tempTarget);
                    }
                }
            }

            // write/upload the file
            writeFile(exchange, tempTarget != null ? tempTarget : target);

            // if we did write to a temporary name then rename it to the real
            // name after we have written the file
            if (tempTarget != null) {
                // if we did not eager delete the target file
                if (endpoint.getFileExist() != GenericFileExist.TryRename && !endpoint.isEagerDeleteTargetFile()) {

                    // if an existing file already exists what should we do?
                    targetExists = operations.existsFile(target);
                    if (targetExists) {

                        LOG.trace("Not using EagerDeleteTargetFile, target exists");

                        if (endpoint.getFileExist() == GenericFileExist.Ignore) {
                            // ignore but indicate that the file was written
                            LOG.trace("An existing file already exists: {}. Ignore and do not override it.", target);
                            return;
                        } else if (endpoint.getFileExist() == GenericFileExist.Fail) {
                            throw new GenericFileOperationFailedException(
                                    "File already exist: " + target + ". Cannot write new file.");
                        } else if (endpoint.getFileExist() == GenericFileExist.Override) {
                            // we override the target so we do this by deleting
                            // it so the temp file can be renamed later
                            // with success as the existing target file have
                            // been deleted
                            LOG.trace("Deleting existing file: {}", target);
                            if (!operations.deleteFile(target)) {
                                throw new GenericFileOperationFailedException("Cannot delete file: " + target);
                            }
                        }
                    }
                }

                // now we are ready to rename the temp file to the target file
                LOG.trace("Renaming file: [{}] to: [{}]", tempTarget, target);
                boolean renamed = operations.renameFile(tempTarget, target);
                if (!renamed) {
                    throw new GenericFileOperationFailedException("Cannot rename file from: " + tempTarget + " to: " + target);
                }
            }

            // any checksum file to write?
            if (endpoint.getChecksumFileAlgorithm() != null) {
                writeChecksumFile(exchange, target);
            }

            // any done file to write?
            if (endpoint.getDoneFileName() != null) {
                String doneFileName = endpoint.createDoneFileName(target);
                StringHelper.notEmpty(doneFileName, "doneFileName", endpoint);

                // create empty exchange with empty body to write as the done
                // file
                Exchange empty = new DefaultExchange(exchange);
                empty.getIn().setBody("");

                LOG.trace("Writing done file: [{}]", doneFileName);
                // delete any existing done file
                if (operations.existsFile(doneFileName)) {
                    if (!operations.deleteFile(doneFileName)) {
                        throw new GenericFileOperationFailedException("Cannot delete existing done file: " + doneFileName);
                    }
                }
                writeFile(empty, doneFileName);
            }

            // let's store the name we really used in the header, so end-users
            // can retrieve it
            exchange.getIn().setHeader(FileConstants.FILE_NAME_PRODUCED, target);
        } catch (Exception e) {
            handleFailedWrite(exchange, e);
        }

        postWriteCheck(exchange);
    }

    protected void writeChecksumFile(Exchange exchange, String target) throws Exception {
        String algorithm = endpoint.getChecksumFileAlgorithm();
        String checksumFileName = target + "." + algorithm;

        // create exchange with checksum as body to write as the checksum file
        MessageHelper.resetStreamCache(exchange.getIn());
        InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
        Exchange checksumExchange = new DefaultExchange(exchange);
        checksumExchange.getIn().setBody(new DigestUtils(algorithm).digestAsHex(is));

        LOG.trace("Writing checksum file: [{}]", checksumFileName);
        // delete any existing done file
        if (operations.existsFile(checksumFileName)) {
            if (!operations.deleteFile(checksumFileName)) {
                throw new GenericFileOperationFailedException(
                        "Cannot delete existing checksum file: " + checksumFileName);
            }
        }
        writeFile(checksumExchange, checksumFileName);
    }

    /**
     * If we fail writing out a file, we will call this method. This hook is provided to disconnect from servers or
     * clean up files we created (if needed).
     */
    public void handleFailedWrite(Exchange exchange, Exception exception) throws Exception {
        throw exception;
    }

    /**
     * Perform any actions that need to occur before we write such as connecting to an FTP server etc.
     */
    public void preWriteCheck(Exchange exchange) throws Exception {
        // nothing needed to check
    }

    /**
     * Perform any actions that need to occur after we are done such as disconnecting.
     */
    public void postWriteCheck(Exchange exchange) {
        // nothing needed to check
    }

    public void writeFile(Exchange exchange, String fileName) throws GenericFileOperationFailedException {
        // build directory if auto create is enabled
        if (endpoint.isAutoCreate()) {
            // we must normalize it (to avoid having both \ and / in the name
            // which confuses java.io.File)
            String name = FileUtil.normalizePath(fileName);

            // use java.io.File to compute the file path
            File file = new File(name);
            String directory = file.getParent();
            boolean absolute = FileUtil.isAbsolute(file);
            if (directory != null) {
                if (!operations.buildDirectory(directory, absolute)) {
                    LOG.debug("Cannot build directory [{}] (could be because of denied permissions)", directory);
                }
            }
        }

        // upload
        if (LOG.isTraceEnabled()) {
            LOG.trace("About to write [{}] to [{}] from exchange [{}]", fileName, getEndpoint(), exchange);
        }

        boolean success = operations.storeFile(fileName, exchange, -1);
        if (!success) {
            throw new GenericFileOperationFailedException("Error writing file [" + fileName + "]");
        }
        LOG.debug("Wrote [{}] to [{}]", fileName, getEndpoint());
    }

    public String createFileName(Exchange exchange) {
        String answer;

        // overrule takes precedence
        Object value;

        Object overrule = exchange.getIn().getHeader(FileConstants.OVERRULE_FILE_NAME);
        if (overrule != null) {
            if (overrule instanceof Expression) {
                value = overrule;
            } else {
                value = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, overrule);
            }
        } else {
            value = exchange.getIn().getHeader(FileConstants.FILE_NAME);
        }

        // if we have an overrule then override the existing header to use the
        // overrule computed name from this point forward
        if (overrule != null) {
            exchange.getIn().setHeader(FileConstants.FILE_NAME, value);
        }

        if (value instanceof String && StringHelper.hasStartToken((String) value, "simple")) {
            LOG.warn(
                    "Simple expression: {} detected in header: {} of type String. This feature has been removed (see CAMEL-6748).",
                    value, FileConstants.FILE_NAME);
        }

        // expression support
        Expression expression = endpoint.getFileName();
        if (value instanceof Expression) {
            expression = (Expression) value;
        }

        // evaluate the name as a String from the value
        String name;
        if (expression != null) {
            LOG.trace("Filename evaluated as expression: {}", expression);
            name = expression.evaluate(exchange, String.class);
        } else {
            name = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
        }

        // flatten name
        if (name != null && endpoint.isFlatten()) {
            // check for both windows and unix separators
            int pos = Math.max(name.lastIndexOf('/'), name.lastIndexOf("\\"));
            if (pos != -1) {
                name = name.substring(pos + 1);
            }
        }

        // compute path by adding endpoint starting directory
        String endpointPath = endpoint.getConfiguration().getDirectory();
        String baseDir = "";
        if (endpointPath.length() > 0) {
            // Its a directory so we should use it as a base path for the
            // filename
            // If the path isn't empty, we need to add a trailing / if it isn't
            // already there
            baseDir = endpointPath;
            boolean trailingSlash = endpointPath.endsWith("/") || endpointPath.endsWith("\\");
            if (!trailingSlash) {
                baseDir += getFileSeparator();
            }
        }
        if (name != null) {
            answer = baseDir + name;
        } else {
            // use a generated filename if no name provided
            answer = baseDir + endpoint.getGeneratedFileName(exchange.getIn());
        }

        if (endpoint.isJailStartingDirectory()) {
            // check for file must be within starting directory (need to compact
            // first as the name can be using relative paths via ../ etc)
            String compatchAnswer = FileUtil.compactPath(answer);
            String compatchBaseDir = FileUtil.compactPath(baseDir);
            if (!compatchAnswer.startsWith(compatchBaseDir)) {
                throw new IllegalArgumentException(
                        "Cannot write file with name: " + compatchAnswer
                                                   + " as the filename is jailed to the starting directory: "
                                                   + compatchBaseDir);
            }
        }

        if (endpoint.getConfiguration().needToNormalize()) {
            // must normalize path to cater for Windows and other OS
            answer = normalizePath(answer);
        }

        return answer;
    }

    public String createTempFileName(Exchange exchange, String fileName) {
        String answer = fileName;

        String tempName;
        if (exchange.getIn().getHeader(FileConstants.FILE_NAME) == null) {
            // its a generated filename then add it to header so we can evaluate
            // the expression
            exchange.getIn().setHeader(FileConstants.FILE_NAME, FileUtil.stripPath(fileName));
            tempName = endpoint.getTempFileName().evaluate(exchange, String.class);
            // and remove it again after evaluation
            exchange.getIn().removeHeader(FileConstants.FILE_NAME);
        } else {
            tempName = endpoint.getTempFileName().evaluate(exchange, String.class);
        }

        // check for both windows and unix separators
        int pos = Math.max(answer.lastIndexOf('/'), answer.lastIndexOf("\\"));
        if (pos == -1) {
            // no path so use temp name as calculated
            answer = tempName;
        } else {
            // path should be prefixed before the temp name
            StringBuilder sb = new StringBuilder(answer.substring(0, pos + 1));
            sb.append(tempName);
            answer = sb.toString();
        }

        if (endpoint.getConfiguration().needToNormalize()) {
            // must normalize path to cater for Windows and other OS
            answer = normalizePath(answer);
        }

        // stack path in case the temporary file uses .. paths
        answer = FileUtil.compactPath(answer, getFileSeparator());

        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(locks);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(locks);
    }
}
