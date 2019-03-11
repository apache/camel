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
import io.nessus.ipfs.client.IPFSClient;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ipfs.IPFSConfiguration.IPFSCommand;

import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The camel-ipfs component provides access to the Interplanetary File System
 * (IPFS).
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "ipfs", title = "IPFS", syntax = "ipfs:host:port/cmd", producerOnly = true, label = "file,ipfs")
public class IPFSEndpoint extends DefaultEndpoint {

    static long defaultTimeout = 10000L;
    
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

    @Override
    public boolean isSingleton() {
        return false;
    }

    IPFSConfiguration getConfiguration() {
        return configuration;
    }

    IPFSCommand getCommand() {
        String cmd = configuration.getIpfsCmd();
        try {
            return IPFSCommand.valueOf(cmd);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported command: " + cmd);
        }
    }

    String ipfsVersion() throws IOException {
        return ipfs().version();
    }

    List<String> ipfsAdd(Path path) throws IOException {
        List<Multihash> cids = ipfs().add(path);
        return cids.stream().map(mh -> mh.toBase58()).collect(Collectors.toList());
    }

    InputStream ipfsCat(String cid) throws IOException, TimeoutException {
        Multihash mhash = Multihash.fromBase58(cid);
        Future<InputStream> future = ipfs().cat(mhash);
        try {
            return future.get(defaultTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException ex) {
            throw new IOException("Cannot obtain: " + cid, ex);
        }
    }

    Path ipfsGet(String cid, Path outdir) throws IOException, TimeoutException {
        Multihash mhash = Multihash.fromBase58(cid);
        Future<Path> future = ipfs().get(mhash, outdir);
        try {
            return future.get(defaultTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException ex) {
            throw new IOException("Cannot obtain: " + cid, ex);
        }
    }

    private IPFSClient ipfs() {
        return getComponent().getIPFSClient();
    }
}
