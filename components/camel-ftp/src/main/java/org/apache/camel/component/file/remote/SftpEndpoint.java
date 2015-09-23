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

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Proxy;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Secure FTP endpoint
 */
@UriEndpoint(scheme = "sftp", extendsScheme = "file", title = "SFTP",
        syntax = "sftp:host:port/directoryName", consumerClass = SftpConsumer.class, label = "file")
public class SftpEndpoint extends RemoteFileEndpoint<ChannelSftp.LsEntry> {

    @UriParam
    protected SftpConfiguration configuration;
    @UriParam(label = "advanced")
    protected Proxy proxy;
    
    public SftpEndpoint() {
    }

    public SftpEndpoint(String uri, SftpComponent component, SftpConfiguration configuration) {
        super(uri, component, configuration);
        this.configuration = configuration;
    }

    @Override
    public SftpConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("SftpConfiguration expected");
        }
        // need to set on both
        this.configuration = (SftpConfiguration) configuration;
        super.setConfiguration(configuration);
    }

    @Override
    protected RemoteFileConsumer<ChannelSftp.LsEntry> buildConsumer(Processor processor) {
        return new SftpConsumer(this, processor, createRemoteFileOperations());
    }

    protected GenericFileProducer<ChannelSftp.LsEntry> buildProducer() {
        return new RemoteFileProducer<ChannelSftp.LsEntry>(this, createRemoteFileOperations());
    }

    public RemoteFileOperations<ChannelSftp.LsEntry> createRemoteFileOperations() {
        SftpOperations operations = new SftpOperations(proxy);
        operations.setEndpoint(this);
        return operations;
    }

    public Proxy getProxy() {
        return proxy;
    }

    /**
     * To use a custom configured com.jcraft.jsch.Proxy.
     * This proxy is used to consume/send messages from the target SFTP host.
     */
    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public String getScheme() {
        return "sftp";
    }
}
