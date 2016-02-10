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
package org.apache.camel.component.etcd;

import javax.net.ssl.SSLContext;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.jsse.SSLContextParameters;

@UriParams
class EtcdConfiguration {

    @UriParam(multiValue = true)
    private String uris;

    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "security")
    private String userName;

    @UriParam(label = "security")
    private String password;


    public String getUris() {
        return uris;
    }

    public boolean hasUris() {
        return uris != null;
    }

    public void setUris(String uris) {
        this.uris = uris;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    SSLContext createSslContext() throws Exception {
        return sslContextParameters != null ? sslContextParameters.createSSLContext() : null;
    }
}
