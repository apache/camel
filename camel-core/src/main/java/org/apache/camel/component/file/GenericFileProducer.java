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

import java.io.IOException;
import java.io.InputStream;

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
public class GenericFileProducer extends DefaultProducer {
    private final transient Log log = LogFactory.getLog(GenericFileProducer.class);
    private GenericFileOperations operations;

    protected GenericFileProducer(GenericFileEndpoint endpoint, GenericFileOperations operations) {
        super(endpoint);
        this.operations = operations;
    }

    /**
     * Convenience method
     */
    protected GenericFileEndpoint getGenericFileEndpoint() {
        return (GenericFileEndpoint) getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        GenericFileExchange fileExchange = (GenericFileExchange) getEndpoint().createExchange(exchange);
        processExchange(fileExchange);
        ExchangeHelper.copyResults(exchange, fileExchange);
    }

    /**
     * Perform the work to process the fileExchange
     *
     * @param exchange fileExchange
     * @throws Exception is thrown if some error
     */
    protected void processExchange(GenericFileExchange exchange) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Processing " + exchange);
        }

        String target = createFileName(exchange);

        // should we write to a temporary name and then afterwards rename to real target
        boolean writeAsTempAndRename = ObjectHelper.isNotEmpty(getGenericFileEndpoint().getTempPrefix());
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
                log.trace("Renaming file: " + tempTarget + " to: " + target);
            }
            boolean renamed = operations.renameFile(tempTarget, target);
            if (!renamed) {
                throw new GenericFileOperationFailedException("Cannot rename file from: " + tempTarget + " to: " + target);
            }
        }

        // lets store the name we really used in the header, so end-users can retrieve it
        exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME_PRODUCED, target);
    }

    /**
     * Perform any actions that need to occur before we write Such as connecting
     * to an FTP server etc.
     */
    protected void preWriteCheck() {
    }

    protected void writeFile(Exchange exchange, String fileName) throws GenericFileOperationFailedException {
        InputStream payload = exchange.getIn().getBody(InputStream.class);
        try {
            // build directory
            int lastPathIndex = fileName.lastIndexOf('/');
            if (lastPathIndex != -1) {
                String directory = fileName.substring(0, lastPathIndex);
                if (!operations.buildDirectory(directory)) {
                    log.warn("Couldn't build directory: " + directory + " (could be because of denied permissions)");
                }
            }
            boolean success = operations.storeFile(fileName, payload);
            if (!success) {
                throw new GenericFileOperationFailedException("Error writing file: " + fileName);
            }
        } finally {
            ObjectHelper.close(payload, "Closing payload", log);
        }
    }

    protected String createFileName(Exchange exchange) {
        String answer;

        String name = exchange.getIn().getHeader(FileComponent.HEADER_FILE_NAME, String.class);

        // expression support
        Expression expression = getGenericFileEndpoint().getExpression();
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

        String endpointFile = getGenericFileEndpoint().getConfiguration().getFile();
        if (getGenericFileEndpoint().getConfiguration().isDirectory()) {
            // If the path isn't empty, we need to add a trailing / if it isn't
            // already there
            String baseDir = "";
            if (endpointFile.length() > 0) {
                baseDir = endpointFile + (endpointFile.endsWith("/") ? "" : "/");
            }
            String fileName = (name != null) ? name : getGenericFileEndpoint().getGeneratedFileName(exchange.getIn());
            answer = baseDir + fileName;
        } else {
            answer = endpointFile;
        }

        return answer;
    }

    protected String createTempFileName(String fileName) {
        int path = fileName.lastIndexOf("/");
        if (path == -1) {
            // no path
            return getGenericFileEndpoint().getTempPrefix() + fileName;
        } else {
            StringBuilder sb = new StringBuilder(fileName);
            sb.insert(path + 1, getGenericFileEndpoint().getTempPrefix());
            return sb.toString();
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Starting");
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Stopping");
        }
        super.doStop();
    }

}
