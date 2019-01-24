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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.ipfs.IPFSConfiguration.IPFSCommand;
import org.apache.camel.support.DefaultProducer;


public class IPFSProducer extends DefaultProducer {

    public IPFSProducer(IPFSEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public IPFSEndpoint getEndpoint() {
        return (IPFSEndpoint)super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        IPFSEndpoint endpoint = getEndpoint();
        IPFSCommand cmd = endpoint.getCommand();

        if (IPFSCommand.version == cmd) {

            String resp = endpoint.ipfsVersion();
            exchange.getMessage().setBody(resp);

        } else if (IPFSCommand.add == cmd) {

            Path path = pathFromBody(exchange);
            List<String> cids = endpoint.ipfsAdd(path);
            Object resp = cids;
            if (path.toFile().isFile()) {
                resp = cids.size() > 0 ? cids.get(0) : null;
            }
            exchange.getMessage().setBody(resp);

        } else if (IPFSCommand.cat == cmd) {

            String cid = exchange.getMessage().getBody(String.class);
            InputStream resp = endpoint.ipfsCat(cid);
            exchange.getMessage().setBody(resp);

        } else if (IPFSCommand.get == cmd) {

            Path outdir = endpoint.getConfiguration().getOutdir();
            String cid = exchange.getMessage().getBody(String.class);
            Path resp = endpoint.ipfsGet(cid, outdir);
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
}
