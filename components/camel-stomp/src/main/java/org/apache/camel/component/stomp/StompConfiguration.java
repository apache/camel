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
package org.apache.camel.component.stomp;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.jsse.SSLContextParameters;

@UriParams
public class StompConfiguration implements Cloneable {
    @UriParam(defaultValue = "tcp://localhost:61613")
    @Metadata(required = "true")
    private String brokerURL = "tcp://localhost:61613";
    @UriParam(label = "security", secret = true)
    private String login;
    @UriParam(label = "security", secret = true)
    private String passcode;
    @UriParam
    private String host;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

    /**
     * Returns a copy of this configuration
     */
    public StompConfiguration copy() {
        try {
            StompConfiguration copy = (StompConfiguration) clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getBrokerURL() {
        return brokerURL;
    }
    
    public String getHost() {
        return host;
    }
    
    /**
     * The virtual host name
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * The URI of the Stomp broker to connect to
     */
    public void setBrokerURL(String brokerURL) {
        this.brokerURL = brokerURL;
    }

    public String getLogin() {
        return login;
    }

    /**
     * The username
     */
    public void setLogin(String login) {
        this.login = login;
    }

    public String getPasscode() {
        return passcode;
    }

    /**
     * The password
     */
    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

}
