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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class IPFSConfiguration {

    // Available commands
    public enum IPFSCommand {
        add, cat, get, version 
    }

    @UriPath(description = "The ipfs host") @Metadata(required = true)
    private String ipfsHost;
    @UriPath(description = "The ipfs port") @Metadata(required = true)
    private int ipfsPort;
    @UriPath(description = "The ipfs command", enums = "add,cat,get,version") @Metadata(required = true)
    private String ipfsCmd;
    @UriParam(description = "The ipfs output directory")
    private String outdir;

    public void init(String urispec, String remaining) throws Exception {
        // Derive host:port and cmd from the give uri
        URI uri = new URI(urispec);
        String host = uri.getHost();
        int port = uri.getPort();
        String cmd = remaining;
        if (!cmd.equals(host)) {
            if (host != null) {
                setIpfsHost(host);
            }
            if (port > 0) {
                setIpfsPort(port);
            }
            int idx = cmd.indexOf('/');
            cmd = cmd.substring(idx + 1);
        }
        setIpfsCmd(cmd);
    }
    
    public String getIpfsCmd() {
        return ipfsCmd;
    }

    public void setIpfsCmd(String cmd) {
        this.ipfsCmd = cmd;
    }

    public String getIpfsHost() {
        return ipfsHost;
    }

    public void setIpfsHost(String ipfsHost) {
        this.ipfsHost = ipfsHost;
    }

    public int getIpfsPort() {
        return ipfsPort;
    }

    public void setIpfsPort(int ipfsPort) {
        this.ipfsPort = ipfsPort;
    }

    public String getOutdir() {
        return outdir;
    }

    public void setOutdir(String outdir) {
        this.outdir = outdir;
    }
}
