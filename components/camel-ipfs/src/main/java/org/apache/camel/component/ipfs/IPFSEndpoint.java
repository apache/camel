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
package org.apache.camel.component.ipfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.ipfs.multihash.Multihash;
import io.nessus.ipfs.client.DefaultIPFSClient;
import io.nessus.ipfs.client.IPFSClient;
import io.nessus.ipfs.client.IPFSException;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ipfs.IPFSConfiguration.IPFSCommand;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The camel-ipfs component provides access to the Interplanetary File System (IPFS).
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "ipfs", title = "IPFS",
        syntax = "ipfs:ipfsHost:ipfsPort/ipfsCmd", producerOnly = true, label = "file,ipfs")
public class IPFSEndpoint extends DefaultEndpoint {

    @UriParam
    private final IPFSConfiguration configuration;

    public IPFSEndpoint(String uri, IPFSComponent component, IPFSConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public IPFSComponent getComponent() {
        return (IPFSComponent)super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IPFSProducer(this);
    }

    public IPFSConfiguration getConfiguration() {
        return configuration;
    }

}
