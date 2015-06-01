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
package org.apache.camel.component.atmos;

import java.net.URI;
import java.net.URISyntaxException;

import com.emc.atmos.api.AtmosApi;
import com.emc.atmos.api.AtmosConfig;
import com.emc.atmos.api.jersey.AtmosApiClient;
import org.apache.camel.component.atmos.util.AtmosException;
import org.apache.camel.component.atmos.util.AtmosOperation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class AtmosConfiguration {

    private AtmosApi client;

    @UriPath
    private String name;
    @UriPath @Metadata(required = "true")
    private AtmosOperation operation;
    @UriParam
    private String secretKey;
    @UriParam
    private String localPath;
    @UriParam
    private String remotePath;
    @UriParam
    private String newRemotePath;
    @UriParam
    private String query;
    @UriParam
    private String fullTokenId;
    @UriParam
    private String uri;
    @UriParam
    private boolean enableSslValidation;

    public void setClient(AtmosApi client) {
        this.client = client;
    }
    
    public AtmosApi getClient() {
        return client;
    }

    /**
     * Obtain a new instance of AtmosApi client and store it in configuration.
     *
     * @throws AtmosException
     */
    public void createClient() throws AtmosException {
        AtmosConfig config = null;
        try {
            config = new AtmosConfig(fullTokenId, secretKey, new URI(uri));
        } catch (URISyntaxException use) {
            throw new AtmosException("wrong syntax for Atmos URI!", use);
        }
        
        if (!enableSslValidation) {
            config.setDisableSslValidation(true);
        }
        AtmosApi atmosclient = new AtmosApiClient(config);
        
        this.client = atmosclient;
    }

    public String getName() {
        return name;
    }

    /**
     * Atmos name
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Atmos shared secret
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    
    public String getLocalPath() {
        return localPath;
    }

    /**
     * Local path to put files
     */
    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
    
    public String getRemotePath() {
        return remotePath;
    }

    /**
     * Where to put files on Atmos
     */
    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }
    
    public String getNewRemotePath() {
        return newRemotePath;
    }

    /**
     * New path on Atmos when moving files
     */
    public void setNewRemotePath(String newRemotePath) {
        this.newRemotePath = newRemotePath;
    }
    
    public String getQuery() {
        return query;
    }

    /**
     * Search query on Atmos
     */
    public void setQuery(String query) {
        this.query = query;
    }
    
    public String getFullTokenId() {
        return fullTokenId;
    }

    /**
     * Atmos client fullTokenId
     */
    public void setFullTokenId(String fullTokenId) {
        this.fullTokenId = fullTokenId;
    }
    
    public AtmosOperation getOperation() {
        return operation;
    }

    /**
     * Operation to perform
     */
    public void setOperation(AtmosOperation operation) {
        this.operation = operation;
    }

    /**
     * Atomos server uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }
    
    public String getUri() {
        return uri;
    }
    
    public boolean isEnableSslValidation() {
        return enableSslValidation;
    }

    /**
     * Atmos SSL validation
     */
    public void setEnableSslValidation(boolean enableSslValidation) {
        this.enableSslValidation = enableSslValidation;
    }
    
}
