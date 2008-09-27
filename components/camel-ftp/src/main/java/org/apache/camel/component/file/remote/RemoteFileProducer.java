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

import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class RemoteFileProducer<T extends RemoteFileExchange> extends DefaultProducer<T> {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected RemoteFileEndpoint<T> endpoint;

    protected RemoteFileProducer(RemoteFileEndpoint<T> endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    protected String createFileName(Message message, RemoteFileConfiguration fileConfig) {
        String answer;

        String name = message.getHeader(FileComponent.HEADER_FILE_NAME, String.class);

        // expression support
        Expression expression = endpoint.getConfiguration().getExpression();
        if (name != null) {
            // the header name can be an expression too, that should override whatever configured on the endpoint
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
            Object result = expression.evaluate(message.getExchange());
            name = message.getExchange().getContext().getTypeConverter().convertTo(String.class, result);
        }        

        String endpointFile = fileConfig.getFile();
        if (fileConfig.isDirectory()) {
            // If the path isn't empty, we need to add a trailing / if it isn't already there
            String baseDir = "";
            if (endpointFile.length() > 0) {
                baseDir = endpointFile + (endpointFile.endsWith("/") ? "" : "/");
            }
            String fileName = (name != null) ? name : endpoint.getGeneratedFileName(message); 
            answer = baseDir + fileName;
        } else {
            answer = endpointFile;
        }

        // lets store the name we really used in the header, so end-users can retrieve it
        message.setHeader(FileComponent.HEADER_FILE_NAME_PRODUCED, answer);

        return answer;
    }

    protected String remoteServer() {
        return endpoint.getConfiguration().remoteServerInformation();
    }

    @Override
    protected void doStart() throws Exception {
        log.info("Starting");
        // do not connect when component starts, just wait until we process as we will
        // connect at that time if needed
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.info("Stopping");
        // disconnect when stopping
        try {
            disconnect();
        } catch (Exception e) {
            // ignore just log a warning
            log.warn("Exception occured during disconecting from " + remoteServer() + ". "
                     + e.getClass().getCanonicalName() + " message: " + e.getMessage());
        }
        super.doStop();
    }

    protected abstract void connectIfNecessary() throws Exception;

    protected abstract void disconnect() throws Exception;
}
