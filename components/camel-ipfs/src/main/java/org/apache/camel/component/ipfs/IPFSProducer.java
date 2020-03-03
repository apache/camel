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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.ipfs.multihash.Multihash;
import io.nessus.ipfs.client.IPFSClient;
import io.nessus.ipfs.client.IPFSException;
import org.apache.camel.Exchange;
import org.apache.camel.component.ipfs.IPFSConfiguration.IPFSCommand;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPFSProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(IPFSComponent.class);

    private static final long DEFAULT_TIMEOUT = 10000L;

    public IPFSProducer(IPFSEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public IPFSEndpoint getEndpoint() {
        return (IPFSEndpoint)super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        IPFSClient client = getEndpoint().getIPFSClient();
        try {
            client.connect();
        } catch (IPFSException ex) {
            LOG.warn(ex.getMessage());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // client has no disconnect api
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        IPFSEndpoint endpoint = getEndpoint();
        IPFSCommand cmd = getCommand();

        if (IPFSCommand.version == cmd) {
            String resp = ipfsVersion();
            exchange.getMessage().setBody(resp);
        } else if (IPFSCommand.add == cmd) {
            Path path = pathFromBody(exchange);
            List<String> cids = ipfsAdd(path);
            Object resp = cids;
            if (path.toFile().isFile()) {
                resp = cids.size() > 0 ? cids.get(0) : null;
            }
            exchange.getMessage().setBody(resp);
        } else if (IPFSCommand.cat == cmd) {
            String cid = exchange.getMessage().getBody(String.class);
            InputStream resp = ipfsCat(cid);
            exchange.getMessage().setBody(resp);
        } else if (IPFSCommand.get == cmd) {
            String outdir = endpoint.getConfiguration().getOutdir();
            String cid = exchange.getMessage().getBody(String.class);
            Path resp = ipfsGet(cid, new File(outdir).toPath());
            exchange.getMessage().setBody(resp);
        } else {
            throw new UnsupportedOperationException(cmd.toString());
        }
    }

    private Path pathFromBody(Exchange exchange) {
        Object body = exchange.getMessage().getBody();
        if (body instanceof Path) {
            return (Path)body;
        }
        if (body instanceof String) {
            return Paths.get((String)body);
        }
        if (body instanceof File) {
            return ((File)body).toPath();
        }
        throw new IllegalArgumentException("Invalid path: " + body);
    }


    public IPFSCommand getCommand() {
        String cmd = getEndpoint().getConfiguration().getIpfsCmd();
        try {
            return IPFSCommand.valueOf(cmd);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported command: " + cmd);
        }
    }

    public String ipfsVersion() throws IOException {
        return ipfs().version();
    }

    public List<String> ipfsAdd(Path path) throws IOException {
        List<Multihash> cids = ipfs().add(path);
        return cids.stream().map(mh -> mh.toBase58()).collect(Collectors.toList());
    }

    public InputStream ipfsCat(String cid) throws IOException, TimeoutException {
        Multihash mhash = Multihash.fromBase58(cid);
        Future<InputStream> future = ipfs().cat(mhash);
        try {
            return future.get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException ex) {
            throw new IOException("Cannot obtain: " + cid, ex);
        }
    }

    public Path ipfsGet(String cid, Path outdir) throws IOException, TimeoutException {
        Multihash mhash = Multihash.fromBase58(cid);
        Future<Path> future = ipfs().get(mhash, outdir);
        try {
            return future.get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException ex) {
            throw new IOException("Cannot obtain: " + cid, ex);
        }
    }

    private IPFSClient ipfs() {
        IPFSClient client = getEndpoint().getIPFSClient();
        if (!client.hasConnection()) {
            client.connect();
        }
        return client;
    }


}
