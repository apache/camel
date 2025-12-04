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

package org.apache.camel.component.milvus;

import io.milvus.client.MilvusClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class MilvusConfiguration implements Cloneable {

    @Metadata(defaultValue = "localhost")
    @UriParam
    private String host = "localhost";

    @Metadata(defaultValue = "19530")
    @UriParam
    private int port = 19530;

    @Metadata(secret = true)
    @UriParam
    private String token;

    @UriParam
    private long timeout = 10000L;

    @Metadata(autowired = true)
    private MilvusClient client;

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

    public String getToken() {
        return token;
    }

    /**
     * Sets the API key to use for authentication
     */
    public void setToken(String token) {
        this.token = token;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets a default timeout for all requests
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public MilvusClient getClient() {
        return client;
    }

    /**
     * Reference to a `io.milvus.client.MilvusClient`.
     */
    public void setClient(MilvusClient client) {
        this.client = client;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public MilvusConfiguration copy() {
        try {
            return (MilvusConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
