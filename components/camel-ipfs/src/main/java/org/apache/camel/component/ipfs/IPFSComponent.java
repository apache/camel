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

import java.util.Map;

import io.nessus.ipfs.client.DefaultIPFSClient;
import io.nessus.ipfs.client.IPFSClient;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("ipfs")
public class IPFSComponent extends DefaultComponent {

    @Metadata(description = "The ipfs host", defaultValue = "127.0.0.1")
    private String ipfsHost = "127.0.0.1";
    @Metadata(description = "The ipfs port", defaultValue = "5001")
    private int ipfsPort = 5001;

    private IPFSClient client;

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

    @Override
    protected Endpoint createEndpoint(String urispec, String remaining, Map<String, Object> parameters) throws Exception {

        IPFSConfiguration config = new IPFSConfiguration();
        IPFSEndpoint endpoint = new IPFSEndpoint(urispec, this, config);
        setProperties(endpoint, parameters);

        String cmd = remaining;
        config.setIpfsCmd(cmd);

        return endpoint;
    }

    public IPFSClient getIPFSClient() {
        if (client == null) {
            client = new DefaultIPFSClient(ipfsHost, ipfsPort);
        }
        return client;
    }
}
