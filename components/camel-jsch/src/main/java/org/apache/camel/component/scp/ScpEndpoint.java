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
package org.apache.camel.component.scp;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * To copy files using the secure copy protocol (SCP).
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "scp", extendsScheme = "ftp", title = "SCP",
        syntax = "scp:host:port/directoryName", producerOnly = true, label = "file",
        excludeProperties = "binary,charset,doneFileName,download,fastExistsCheck,fileExist,moveExisting,passiveMode"
                + ",separator,tempFileName,tempPrefix,eagerDeleteTargetFile,keepLastModified,sendNoop"
                + ",maximumReconnectAttempts,reconnectDelay,autoCreate,bufferSize,siteCommand,stepwise,throwExceptionOnConnectFailed")
public class ScpEndpoint extends RemoteFileEndpoint<ScpFile> {

    @UriParam
    private ScpConfiguration configuration;

    public ScpEndpoint() {
    }

    public ScpEndpoint(String uri, ScpComponent component, ScpConfiguration configuration) {
        super(uri, component, configuration);
        this.configuration = configuration;
    }

    @Override
    public ScpConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    protected RemoteFileConsumer<ScpFile> buildConsumer(Processor processor) {
        throw new UnsupportedOperationException("This component does not support consuming from this endpoint");
    }

    @Override
    protected GenericFileProducer<ScpFile> buildProducer() {
        return new ScpProducer(this, createRemoteFileOperations());
    }

    @Override
    public RemoteFileOperations<ScpFile> createRemoteFileOperations() {
        ScpOperations operations = new ScpOperations();
        operations.setEndpoint(this);
        return operations;
    }

    @Override
    public String getScheme() {
        return "scp";
    }
    
    @Override
    public Expression getTempFileName() {
        log.debug("Creation of temporary files not supported by the scp: protocol.");
        return null;
    }
}
