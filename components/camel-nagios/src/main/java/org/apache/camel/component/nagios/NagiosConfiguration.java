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
package org.apache.camel.component.nagios;

import java.net.URI;

import com.googlecode.jsendnsca.NagiosSettings;
import com.googlecode.jsendnsca.encryption.Encryption;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.StringHelper;

@UriParams
public class NagiosConfiguration implements Cloneable {

    private transient NagiosSettings nagiosSettings;

    @UriPath @Metadata(required = true)
    private String host;
    @UriPath @Metadata(required = true)
    private int port;
    @UriParam(defaultValue = "5000")
    private int connectionTimeout = 5000;
    @UriParam(defaultValue = "5000")
    private int timeout = 5000;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "security")
    private Encryption encryption = Encryption.NONE;

    /**
     * Returns a copy of this configuration
     */
    public NagiosConfiguration copy() {
        try {
            NagiosConfiguration copy = (NagiosConfiguration) clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void configure(URI uri) {
        String value = uri.getHost();
        if (value != null) {
            setHost(value);
        }
        int port = uri.getPort();
        if (port > 0) {
            setPort(port);
        }
    }

    public synchronized NagiosSettings getOrCreateNagiosSettings() {
        if (nagiosSettings == null) {

            // validate parameters
            StringHelper.notEmpty(host, "host", this);
            if (port <= 0) {
                throw new IllegalArgumentException("Port must be a positive number on " + this);
            }

            // create settings
            nagiosSettings = new NagiosSettings();
            nagiosSettings.setConnectTimeout(getConnectionTimeout());
            nagiosSettings.setTimeout(getTimeout());
            nagiosSettings.setNagiosHost(getHost());
            nagiosSettings.setPort(getPort());
            nagiosSettings.setPassword(getPassword());
            nagiosSettings.setEncryption(encryption);
        }

        return nagiosSettings;
    }

    public String getHost() {
        return host;
    }

    /**
     * This is the address of the Nagios host where checks should be send.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * The port number of the host.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Connection timeout in millis.
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Sending timeout in millis.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to be authenticated when sending checks to Nagios.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public Encryption getEncryption() {
        return encryption;
    }

    /**
     * To specify an encryption method.
     */
    public void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }

}
