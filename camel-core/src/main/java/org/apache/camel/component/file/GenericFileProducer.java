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

import java.io.InputStream;
import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Generic file producer
 */
public class GenericFileProducer<T> extends DefaultProducer {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected final GenericFileEndpoint<T> endpoint;
    protected final GenericFileOperations<T> operations;

    protected GenericFileProducer(GenericFileEndpoint<T> endpoint, GenericFileOperations<T> operations) {
        super(endpoint);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        GenericFileExchange<T> fileExchange = (GenericFileExchange<T>) endpoint.createExchange(exchange);
        processExchange(fileExchange);
        ExchangeHelper.copyResults(exchange, fileExchange);
    }

    /**
     * Perform the work to process the fileExchange
     *
     * @param exchange fileExchange
     * @throws Exception is thrown if some error
     */
    protected void processExchange(GenericFileExchange<T> exchange) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Processing " + exchange);
        }

        try {
            String target = createFileName(exchange);

            preWriteCheck();

            // should we write to a temporary name and then afterwards rename to real target
            boolean writeAsTempAndRename = ObjectHelper.isNotEmpty(endpoint.getTempPrefix());
            String tempTarget = null;
            if (writeAsTempAndRename) {
                // compute temporary name with the temp prefix
                tempTarget = createTempFileName(target);
            }

            // upload the file
            writeFile(exchange, tempTarget != null ? tempTarget : target);

            // if we did write to a temporary name then rename it to the real
            // name after we have written the file
            if (tempTarget != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Renaming file: [" + tempTarget + "] to: [" + target + "]");
                }
                boolean renamed = operations.renameFile(tempTarget, target);
                if (!renamed) {
                    throw new GenericFileOperationFailedException("Cannot rename file from: " + tempTarget + " to: " + target);
                }
            }

            // lets store the name we really used in the header, so end-users
            // can retrieve it
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME_PRODUCED, target);
        } catch (Exception e) {
            handleFailedWrite(exchange, e);
        }
    }

    /**
     * If we fail writing out a file, we will call this method. This hook is
     * provided to disconnect from servers or clean up files we created (if needed).
     */
    protected void handleFailedWrite(GenericFileExchange<T> exchange, Exception exception) throws Exception {
        throw exception;
    }

    /**
     * Perform any actions that need to occur before we write Such as connecting
     * to an FTP server etc.
     */
    protected void preWriteCheck() throws Exception {
    }

    protected void writeFile(GenericFileExchange<T> exchange, String fileName) throws GenericFileOperationFailedException {
        InputStream payload = exchange.getIn().getBody(InputStream.class);
        try {
            // build directory
            int lastPathIndex = fileName.lastIndexOf(File.separator);
            if (lastPathIndex != -1) {
                String directory = fileName.substring(0, lastPathIndex);
                if (!operations.buildDirectory(directory, false)) {
                    log.debug("Can not build directory [" + directory + "] (could be because of denied permissions)");
                }
            }
            // upload
            if (log.isTraceEnabled()) {
                log.trace("About to write [" + fileName + "] to [" + getEndpoint() + "] from exchange [" + exchange + "]");
            }

            boolean success = operations.storeFile(fileName, exchange);
            if (!success) {
                throw new GenericFileOperationFailedException("Error writing file [" + fileName + "]");
            }
            if (log.isDebugEnabled()) {
                log.debug("Wrote [" + fileName + "] to [" + getEndpoint() + "]");
            }

        } finally {
            ObjectHelper.close(payload, "Closing payload", log);
        }

    }

    protected String createFileName(Exchange exchange) {
        String answer;

        String name = exchange.getIn().getHeader(FileComponent.HEADER_FILE_NAME, String.class);

        // expression support
        Expression expression = endpoint.getExpression();
        if (name != null) {
            // the header name can be an expression too, that should override
            // whatever configured on the endpoint
            if (name.indexOf("${") > -1) {
                if (log.isDebugEnabled()) {
                    log.debug(FileComponent.HEADER_FILE_NAME + " contains a FileLanguage expression: " + name);
                }
                expression = FileLanguage.file(name);
            }
        }
        if (expression != null) {
            if (log.isDebugEnabled()) {
                log.debug("Filename evaluated as expression: " + expression);
            }
            Object result = expression.evaluate(exchange);
            name = exchange.getContext().getTypeConverter().convertTo(String.class, result);
        }

        String endpointFile = endpoint.getConfiguration().getFile();
        if (endpoint.isDirectory()) {
            // Its a directory so we should use it as a basepath for the filename
            // If the path isn't empty, we need to add a trailing / if it isn't already there
            String baseDir = "";
            if (endpointFile.length() > 0) {
                baseDir = endpointFile + (endpointFile.endsWith(File.separator) ? "" : File.separator);
            }
            if (name != null) {
                answer = baseDir + name;
            } else {
                // use a generated filename if no name provided
                // TODO: Consider to require end user to always provide a filename instead of generating a new name
                answer = baseDir + endpoint.getGeneratedFileName(exchange.getIn());
            }
        } else {
            answer = endpointFile;
        }

        return answer;
    }

    protected String createTempFileName(String fileName) {
        int path = fileName.lastIndexOf(File.separator);
        if (path == -1) {
            // no path
            return endpoint.getTempPrefix() + fileName;
        } else {
            StringBuilder sb = new StringBuilder(fileName);
            sb.insert(path + 1, endpoint.getTempPrefix());
            return sb.toString();
        }
    }

}
