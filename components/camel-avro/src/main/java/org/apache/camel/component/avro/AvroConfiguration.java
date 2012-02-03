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

package org.apache.camel.component.avro;

import java.net.URI;
import java.util.Map;
import org.apache.avro.Protocol;
import org.apache.camel.RuntimeCamelException;

public class AvroConfiguration implements Cloneable {

    private String host;
    private int port;
    private Protocol protocol;
    private String protocolLocation;
    private String protocolClassName;
    private String transport;


    public AvroConfiguration copy() {
        try {
            AvroConfiguration answer = (AvroConfiguration) clone();
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void parseURI(URI uri, Map<String, Object> parameters, AvroComponent component) throws Exception {
        transport = uri.getScheme();

        if ((!transport.equalsIgnoreCase("http")) && (!transport.equalsIgnoreCase("netty"))) {
            throw new IllegalArgumentException("Unrecognized Avro IPC transport: " + protocol + " for uri: " + uri);
        }

        setHost(uri.getHost());
        setPort(uri.getPort());
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getProtocolLocation() {
        return protocolLocation;
    }

    public void setProtocolLocation(String protocolLocation) {
        this.protocolLocation = protocolLocation;
    }

    public String getProtocolClassName() {
        return protocolClassName;
    }

    public void setProtocolClassName(String protocolClassName) {
        this.protocolClassName = protocolClassName;
    }
}
