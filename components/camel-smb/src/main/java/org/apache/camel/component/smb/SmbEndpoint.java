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
package org.apache.camel.component.smb;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * SMB component which consumes natively from file shares using the Server Message Block (SMB, also known as Common
 * Internet File System - CIFS) protocol
 */
@UriEndpoint(firstVersion = "4.2.0-SNAPSHOT", scheme = "smb", title = "SMB", syntax = "smb:hostname:port",
             category = { Category.FILE })
public class SmbEndpoint extends DefaultEndpoint {
    @UriParam
    private SmbConfiguration configuration = new SmbConfiguration();

    @UriPath(label = "common")
    @Metadata(required = true)
    private String hostname;

    @UriPath(defaultValue = "445")
    private int port;

    @UriPath
    private String shareName;

    public SmbEndpoint() {
    }

    public SmbEndpoint(String uri, SmbComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException("SMB producer is not supported.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new SmbConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public SmbConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SmbConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The share name or IP address
     *
     * @param hostname
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * The share port
     *
     * @param port
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getShareName() {
        return shareName;
    }

    /**
     * The share path
     *
     * @param shareName
     */
    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

}
