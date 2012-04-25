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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic file producer
 */
public class GenericFileProducer<T> extends DefaultProducer {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    protected final GenericFileEndpoint<T> endpoint;
    protected GenericFileOperations<T> operations;
    // assume writing to 100 different files concurrently at most for the same file producer
    private final LRUCache<String, Lock> locks = new LRUCache<String, Lock>(100);

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

    public void process(Exchange exchange) throws Exception {
        endpoint.configureExchange(exchange);

        String target = createFileName(exchange);

        // use lock for same file name to avoid concurrent writes to the same file
        // for example when you concurrently append to the same file
        Lock lock;
        synchronized (locks) {
            lock = locks.get(target);
            if (lock == null) {
                lock = new ReentrantLock();
                locks.put(target, lock);
            }
        }

        lock.lock();
        try {
            processExchange(exchange, target);
        } finally {
            // do not remove as the locks cache has an upper bound
            // this ensure the locks is appropriate reused
            lock.unlock();
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
     * @param exchange fileExchange
     * @param target   the target filename
     * @throws Exception is thrown if some error
     */
    protected void processExchange(Exchange exchange, String target) throws Exception {
        log.trace("Processing file: {} for exchange: {}", target, exchange);

        try {
            preWriteCheck();

            // should we write to a temporary name and then afterwards rename to real target
            boolean writeAsTempAndRename = ObjectHelper.isNotEmpty(endpoint.getTempFileName());
            String tempTarget = null;
            // remember if target exists to avoid checking twice
            Boolean targetExists = null;
            if (writeAsTempAndRename) {
                // compute temporary name with the temp prefix
                tempTarget = createTempFileName(exchange, target);

                log.trace("Writing using tempNameFile: {}", tempTarget);

                // cater for file exists option on the real target as
                // the file operations code will work on the temp file

                // if an existing file already exists what should we do?
                targetExists = operations.existsFile(target);
                if (targetExists) {
                    if (endpoint.getFileExist() == GenericFileExist.Ignore) {
                        // ignore but indicate that the file was written
                        log.trace("An existing file already exists: {}. Ignore and do not override it.", target);
                        return;
                    } else if (endpoint.getFileExist() == GenericFileExist.Fail) {
                        throw new GenericFileOperationFailedException("File already exist: " + target + ". Cannot write new file.");
                    } else if (endpoint.isEagerDeleteTargetFile() && endpoint.getFileExist() == GenericFileExist.Override) {
                        // we override the target so we do this by deleting it so the temp file can be renamed later
                        // with success as the existing target file have been deleted
                        log.trace("Eagerly deleting existing file: {}", target);
                        if (!operations.deleteFile(target)) {
                            throw new GenericFileOperationFailedException("Cannot delete file: " + target);
                        }
                    }
                }

                // delete any pre existing temp file
                if (operations.existsFile(tempTarget)) {
                    log.trace("Deleting existing temp file: {}", tempTarget);
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

                // if we should not eager delete the target file then do it now just before renaming
                if (!endpoint.isEagerDeleteTargetFile() && targetExists
                        && endpoint.getFileExist() == GenericFileExist.Override) {
                    // we override the target so we do this by deleting it so the temp file can be renamed later
                    // with success as the existing target file have been deleted
                    log.trace("Deleting existing file: {}", target);
                    if (!operations.deleteFile(target)) {
                        throw new GenericFileOperationFailedException("Cannot delete file: " + target);
                    }
                }

                // now we are ready to rename the temp file to the target file
                log.trace("Renaming file: [{}] to: [{}]", tempTarget, target);
                boolean renamed = operations.renameFile(tempTarget, target);
                if (!renamed) {
                    throw new GenericFileOperationFailedException("Cannot rename file from: " + tempTarget + " to: " + target);
                }
            }

            // any done file to write?
            if (endpoint.getDoneFileName() != null) {
                String doneFileName = endpoint.createDoneFileName(target);
                ObjectHelper.notEmpty(doneFileName, "doneFileName", endpoint);

                // create empty exchange with empty body to write as the done file
                Exchange empty = new DefaultExchange(exchange);
                empty.getIn().setBody("");

                log.trace("Writing done file: [{}]", doneFileName);
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
            exchange.getIn().setHeader(Exchange.FILE_NAME_PRODUCED, target);
        } catch (Exception e) {
            handleFailedWrite(exchange, e);
        }

        postWriteCheck();
    }

    /**
     * If we fail writing out a file, we will call this method. This hook is
     * provided to disconnect from servers or clean up files we created (if needed).
     */
    public void handleFailedWrite(Exchange exchange, Exception exception) throws Exception {
        throw exception;
    }

    /**
     * Perform any actions that need to occur before we write such as connecting to an FTP server etc.
     */
    public void preWriteCheck() throws Exception {
        // nothing needed to check
    }

    /**
     * Perform any actions that need to occur after we are done such as disconnecting.
     */
    public void postWriteCheck() {
        // nothing needed to check
    }

    public void writeFile(Exchange exchange, String fileName) throws GenericFileOperationFailedException {
        // build directory if auto create is enabled
        if (endpoint.isAutoCreate()) {
            // we must normalize it (to avoid having both \ and / in the name which confuses java.io.File)
            String name = FileUtil.normalizePath(fileName);

            // use java.io.File to compute the file path
            File file = new File(name);
            String directory = file.getParent();
            boolean absolute = FileUtil.isAbsolute(file);
            if (directory != null) {
                if (!operations.buildDirectory(directory, absolute)) {
                    log.debug("Cannot build directory [{}] (could be because of denied permissions)", directory);
                }
            }
        }

        // upload
        if (log.isTraceEnabled()) {
            log.trace("About to write [{}] to [{}] from exchange [{}]", new Object[]{fileName, getEndpoint(), exchange});
        }

        boolean success = operations.storeFile(fileName, exchange);
        if (!success) {
            throw new GenericFileOperationFailedException("Error writing file [" + fileName + "]");
        }
        log.debug("Wrote [{}] to [{}]", fileName, getEndpoint());
    }

    public String createFileName(Exchange exchange) {
        String answer;

        String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);

        // expression support
        Expression expression = endpoint.getFileName();
        if (name != null) {
            // the header name can be an expression too, that should override
            // whatever configured on the endpoint
            if (StringHelper.hasStartToken(name, "simple")) {
                log.trace("{} contains a Simple expression: {}", Exchange.FILE_NAME, name);
                Language language = getEndpoint().getCamelContext().resolveLanguage("file");
                expression = language.createExpression(name);
            }
        }
        if (expression != null) {
            log.trace("Filename evaluated as expression: {}", expression);
            name = expression.evaluate(exchange, String.class);
        }

        // flatten name
        if (name != null && endpoint.isFlatten()) {
            // check for both windows and unix separators
            int pos = Math.max(name.lastIndexOf("/"), name.lastIndexOf("\\"));
            if (pos != -1) {
                name = name.substring(pos + 1);
            }
        }

        // compute path by adding endpoint starting directory
        String endpointPath = endpoint.getConfiguration().getDirectory();
        String baseDir = "";
        if (endpointPath.length() > 0) {
            // Its a directory so we should use it as a base path for the filename
            // If the path isn't empty, we need to add a trailing / if it isn't already there
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

        if (endpoint.getConfiguration().needToNormalize()) {
            // must normalize path to cater for Windows and other OS
            answer = normalizePath(answer);
        }

        return answer;
    }

    public String createTempFileName(Exchange exchange, String fileName) {
        String answer = fileName;

        String tempName;
        if (exchange.getIn().getHeader(Exchange.FILE_NAME) == null) {
            // its a generated filename then add it to header so we can evaluate the expression
            exchange.getIn().setHeader(Exchange.FILE_NAME, FileUtil.stripPath(fileName));
            tempName = endpoint.getTempFileName().evaluate(exchange, String.class);
            // and remove it again after evaluation
            exchange.getIn().removeHeader(Exchange.FILE_NAME);
        } else {
            tempName = endpoint.getTempFileName().evaluate(exchange, String.class);
        }

        // check for both windows and unix separators
        int pos = Math.max(answer.lastIndexOf("/"), answer.lastIndexOf("\\"));
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

        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(locks);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(locks);
        super.doStop();
    }
}
