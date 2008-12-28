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
package org.apache.camel.component.file.remote;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Remote file producer
 */
public class RemoteFileProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(RemoteFileProducer.class);
    private RemoteFileEndpoint endpoint;
    private RemoteFileOperations operations;
    private boolean loggedIn;

    protected RemoteFileProducer(RemoteFileEndpoint endpoint, RemoteFileOperations operations) {
        super(endpoint);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    public void process(Exchange exchange) throws Exception {
        RemoteFileExchange remoteExchange = (RemoteFileExchange) endpoint.createExchange(exchange);
        processExchange(remoteExchange);
        ExchangeHelper.copyResults(exchange, remoteExchange);
    }

    protected void processExchange(RemoteFileExchange exchange) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing " + exchange);
        }

        try {
            connectIfNecessary();

            if (!loggedIn) {
                // must be logged in to be able to upload the file
                String message = "Could not connect/login to: " + endpoint.remoteServerInformation();
                throw new RemoteFileOperationFailedException(message);
            }

            String target = createFileName(exchange);

            // should we write to a temporary name and then afterwards rename to real target
            boolean writeAsTempAndRename = ObjectHelper.isNotEmpty(endpoint.getTempPrefix());
            String tempTarget = null;
            if (writeAsTempAndRename) {
                // compute temporary name with the temp prefix
                tempTarget = createTempFileName(target);
            }

            // upload the file
            writeFile(exchange, tempTarget != null ? tempTarget : target);

            // if we did write to a temporary name then rename it to the real name after we have written the file
            if (tempTarget != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Renaming file: " + tempTarget + " to: " + target);
                }
                boolean renamed = operations.renameFile(tempTarget, target);
                if (!renamed) {
                    throw new RemoteFileOperationFailedException("Cannot rename file from: " + tempTarget + " to: " + target);
                }
            }

            // lets store the name we really used in the header, so end-users can retrieve it
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME_PRODUCED, target);

        } catch (Exception e) {
            loggedIn = false;
            if (isStopping() || isStopped()) {
                // if we are stopping then ignore any exception during a poll
                LOG.debug("Exception occurd during stopping. " + e.getMessage());
            } else {
                LOG.debug("Exception occurd during processing.", e);
                disconnect();
                // Rethrow to signify that we didn't poll
                throw e;
            }
        }
    }

    protected void writeFile(Exchange exchange, String fileName) throws RemoteFileOperationFailedException, IOException {
        InputStream payload = exchange.getIn().getBody(InputStream.class);
        try {
            // build directory
            int lastPathIndex = fileName.lastIndexOf('/');
            if (lastPathIndex != -1) {
                String directory = fileName.substring(0, lastPathIndex);
                if (!operations.buildDirectory(directory)) {
                    LOG.warn("Couldn't build directory: " + directory + " (could be because of denied permissions)");
                }
            }

            // upload
            if (LOG.isTraceEnabled()) {
                LOG.trace("About to send: " + fileName + " to: " + remoteServer() + " from exchange: " + exchange);
            }

            boolean success = operations.storeFile(fileName, payload);
            if (!success) {
                throw new RemoteFileOperationFailedException("Error sending file: " + fileName + " to: " + remoteServer());
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Sent: " + fileName + " to: " + remoteServer());
            }
        } finally {
            ObjectHelper.close(payload, "Closing payload", LOG);
        }
    }

    protected String createFileName(Exchange exchange) {
        String answer;

        String name = exchange.getIn().getHeader(FileComponent.HEADER_FILE_NAME, String.class);

        // expression support
        Expression expression = endpoint.getExpression();
        if (name != null) {
            // the header name can be an expression too, that should override whatever configured on the endpoint
            if (name.indexOf("${") > -1) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(FileComponent.HEADER_FILE_NAME + " contains a FileLanguage expression: " + name);
                }
                expression = FileLanguage.file(name);
            }
        }
        if (expression != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Filename evaluated as expression: " + expression);
            }
            Object result = expression.evaluate(exchange);
            name = exchange.getContext().getTypeConverter().convertTo(String.class, result);
        }

        String endpointFile = endpoint.getConfiguration().getFile();
        if (endpoint.getConfiguration().isDirectory()) {
            // If the path isn't empty, we need to add a trailing / if it isn't already there
            String baseDir = "";
            if (endpointFile.length() > 0) {
                baseDir = endpointFile + (endpointFile.endsWith("/") ? "" : "/");
            }
            String fileName = (name != null) ? name : endpoint.getGeneratedFileName(exchange.getIn());
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
            return endpoint.getTempPrefix() + fileName;
        } else {
            StringBuilder sb = new StringBuilder(fileName);
            sb.insert(path + 1, endpoint.getTempPrefix());
            return sb.toString();
        }
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Starting");
        // do not connect when component starts, just wait until we process as we will
        // connect at that time if needed
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping");
        try {
            disconnect();
        } catch (Exception e) {
            // ignore by logging it
            LOG.debug("Exception occured during disconnecting from " + remoteServer() + " " + e.getMessage());
        }
        super.doStop();
    }

    protected void connectIfNecessary() throws IOException {
        if (!operations.isConnected() || !loggedIn) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not connected/logged in, connecting to " + remoteServer());
            }
            loggedIn = operations.connect(endpoint.getConfiguration());
            if (!loggedIn) {
                return;
            }
            LOG.info("Connected and logged in to " + remoteServer());
        }
    }

    public void disconnect() throws IOException {
        loggedIn = false;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Disconnecting from " + remoteServer());
        }
        operations.disconnect();
    }

    protected String remoteServer() {
        return endpoint.remoteServerInformation();
    }
}
