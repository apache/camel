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
package org.apache.camel.component.jsch;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileOperations;

/**
 * Secure Copy Endpoint
 */
public class ScpEndpoint extends RemoteFileEndpoint<ScpFile> {

    public ScpEndpoint() {
    }

    public ScpEndpoint(String uri, JschComponent component, RemoteFileConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public ScpConfiguration getConfiguration() {
        return (ScpConfiguration) this.configuration;
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
