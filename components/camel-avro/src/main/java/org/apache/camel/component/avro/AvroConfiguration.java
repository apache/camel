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
import org.apache.commons.lang.StringUtils;
import static org.apache.camel.component.avro.AvroConstants.*;

public class AvroConfiguration implements Cloneable {

    private String host;
    private int port;
    private Protocol protocol;
    private String protocolLocation;
    private String protocolClassName;
    private String transport;
    private String messageName;
    private String uriAuthority;
    private boolean reflectionProtocol;
    private boolean singleParameter;

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

        if ((!AVRO_HTTP_TRANSPORT.equalsIgnoreCase(transport)) && (!AVRO_NETTY_TRANSPORT.equalsIgnoreCase(transport))) {
            throw new IllegalArgumentException("Unrecognized Avro IPC transport: " + protocol + " for uri: " + uri);
        }

        setHost(uri.getHost());
        setPort(uri.getPort());
        
        if((uri.getPath() != null) && (StringUtils.indexOf(uri.getPath(), AVRO_MESSAGE_NAME_SEPARATOR) != -1)) {
        	String path = StringUtils.substringAfter(uri.getPath(), AVRO_MESSAGE_NAME_SEPARATOR);
        	if(!path.contains(AVRO_MESSAGE_NAME_SEPARATOR)) setMessageName(path);
        	else throw new IllegalArgumentException("Unrecognized Avro message name: " + path + " for uri: " + uri);
        }
        
        setUriAuthority(uri.getAuthority());
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

	public String getMessageName() {
		return messageName;
	}

	public void setMessageName(String messageName) {
		this.messageName = messageName;
	}

	public String getUriAuthority() {
		return uriAuthority;
	}

	public void setUriAuthority(String uriAuthority) {
		this.uriAuthority = uriAuthority;
	}
	
	public boolean isReflectionProtocol() {
		return reflectionProtocol;
	}

	public void setReflectionProtocol(boolean isReflectionProtocol) {
		this.reflectionProtocol = isReflectionProtocol;
	}

	public boolean isSingleParameter() {
		return singleParameter;
	}

	public void setSingleParameter(boolean singleParameter) {
		this.singleParameter = singleParameter;
	}
}
