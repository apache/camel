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
package org.apache.camel.component.qdrant;

import java.time.Duration;

import io.qdrant.client.QdrantClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class QdrantConfiguration implements Cloneable {

    @Metadata(defaultValue = "localhost")
    @UriParam
    private String host = "localhost";

    @Metadata(defaultValue = "6334")
    @UriParam
    private int port = 6334;

    @Metadata(defaultValue = "false")
    @UriParam
    private boolean tls;

    @Metadata(secret = true)
    @UriParam
    private String apiKey;

    @UriParam
    private Duration timeout;

    @Metadata(autowired = true)
    private QdrantClient client;

    public String getHost() {
        return host;
    }

    /**
     * The host to connect to.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * The port to connect to.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public boolean isTls() {
        return tls;
    }

    /**
     * Whether the client uses Transport Layer Security (TLS) to secure communications
     */
    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * Sets the API key to use for authentication
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Sets a default timeout for all requests
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public QdrantClient getClient() {
        return client;
    }

    /**
     * Reference to a `io.qdrant.client.QdrantClient`.
     */
    public void setClient(QdrantClient client) {
        this.client = client;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public QdrantConfiguration copy() {
        try {
            return (QdrantConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
