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
package org.apache.camel.component.pinecone;

import io.pinecone.clients.Pinecone;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class PineconeVectorDbConfiguration implements Cloneable {

    @Metadata(secret = true)
    @UriParam
    private String token;

    @Metadata(autowired = true)
    private Pinecone client;

    public String getToken() {
        return token;
    }

    /**
     * Sets the API key to use for authentication
     */
    public void setToken(String token) {
        this.token = token;
    }

    public Pinecone getClient() {
        return client;
    }

    /**
     * Reference to a `io.pinecone.clients.Pinecone`.
     */
    public void setClient(Pinecone client) {
        this.client = client;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public PineconeVectorDbConfiguration copy() {
        try {
            return (PineconeVectorDbConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
