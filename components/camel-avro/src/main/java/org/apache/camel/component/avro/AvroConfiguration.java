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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.commons.lang.StringUtils;

import static org.apache.camel.component.avro.AvroConstants.AVRO_MESSAGE_NAME_SEPARATOR;

@UriParams
public class AvroConfiguration implements Cloneable {

    @UriPath @Metadata(required = "true")
    private AvroTransport transport;
    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath @Metadata(required = "true")
    private int port;
    @UriPath
    private String messageName;
    @UriParam
    private String protocolLocation;
    @UriParam
    private Protocol protocol;
    @UriParam
    private String protocolClassName;
    @UriParam
    private String uriAuthority;
    @UriParam
    private boolean reflectionProtocol;
    @UriParam
    private boolean singleParameter;

    public AvroConfiguration copy() {
        try {
            AvroConfiguration answer = (AvroConfiguration)clone();
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void parseURI(URI uri, Map<String, Object> parameters, AvroComponent component) throws Exception {
        transport = AvroTransport.valueOf(uri.getScheme());

        setHost(uri.getHost());
        setPort(uri.getPort());
        
        if ((uri.getPath() != null)
            && (StringUtils.indexOf(uri.getPath(), AVRO_MESSAGE_NAME_SEPARATOR) != -1)) {
            String path = StringUtils.substringAfter(uri.getPath(), AVRO_MESSAGE_NAME_SEPARATOR);
            if (!path.contains(AVRO_MESSAGE_NAME_SEPARATOR)) {
                setMessageName(path);
            } else {
                throw new IllegalArgumentException("Unrecognized Avro message name: " + path + " for uri: " + uri);
            }
        }
        
        setUriAuthority(uri.getAuthority());
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname to use
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port number to use
     */
    public void setPort(int port) {
        this.port = port;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Avro protocol to use
     */
    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public AvroTransport getTransport() {
        return transport;
    }

    /**
     * Transport to use
     */
    public void setTransport(String transport) {
        this.transport = AvroTransport.valueOf(transport);
    }

    public void setTransport(AvroTransport transport) {
        this.transport = transport;
    }

    public String getProtocolLocation() {
        return protocolLocation;
    }

    /**
     * Avro protocol location
     */
    public void setProtocolLocation(String protocolLocation) {
        this.protocolLocation = protocolLocation;
    }

    public String getProtocolClassName() {
        return protocolClassName;
    }

    /**
     * Avro protocol to use defined by the FQN class name
     */
    public void setProtocolClassName(String protocolClassName) {
        this.protocolClassName = protocolClassName;
    }

    public String getMessageName() {
        return messageName;
    }

    /**
     * The name of the message to send.
     */
    public void setMessageName(String messageName) {
        this.messageName = messageName;
    }

    public String getUriAuthority() {
        return uriAuthority;
    }

    /**
     * Authority to use (username and password)
     */
    public void setUriAuthority(String uriAuthority) {
        this.uriAuthority = uriAuthority;
    }

    public boolean isReflectionProtocol() {
        return reflectionProtocol;
    }

    /**
     * If protocol object provided is reflection protocol. Should be used only with protocol parameter because for protocolClassName protocol type will be auto detected
     */
    public void setReflectionProtocol(boolean isReflectionProtocol) {
        this.reflectionProtocol = isReflectionProtocol;
    }

    public boolean isSingleParameter() {
        return singleParameter;
    }

    /**
     * If true, consumer parameter won't be wrapped into array. Will fail if protocol specifies more then 1 parameter for the message
     */
    public void setSingleParameter(boolean singleParameter) {
        this.singleParameter = singleParameter;
    }
}
