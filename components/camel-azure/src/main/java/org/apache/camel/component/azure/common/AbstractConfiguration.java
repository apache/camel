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
package org.apache.camel.component.azure.common;

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import org.apache.camel.spi.UriParam;

public abstract class AbstractConfiguration implements Cloneable {

    @UriParam
    private StorageCredentials credentials;

    @UriParam(label = "security", secret = true)
    private String credentialsAccountKey;

    @UriParam(label = "security", secret = true)
    private String credentialsAccountName;
    
    private String accountName;
    
    public String getAccountName() {
        return accountName;
    }

    /**
     * Set the Azure account name
     */
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    
    public StorageCredentials getCredentials() {
        return credentials;
    }

    /**
     * Set the storage credentials, required in most cases
     */
    public void setCredentials(StorageCredentials credentials) {
        this.credentials = credentials;
    }

    public String getCredentialsAccountKey() {
        return credentialsAccountKey;
    }

    /**
     * Set the storage account key used during authentication phase
     */
    public void setCredentialsAccountKey(String credentialsAccountKey) {
        this.credentialsAccountKey = credentialsAccountKey;
    }

    public String getCredentialsAccountName() {
        return credentialsAccountName;
    }

    /**
     * Set the storage account name used during authentication phase
     */
    public void setCredentialsAccountName(String credentialsAccountName) {
        this.credentialsAccountName = credentialsAccountName;
    }

    public  StorageCredentials getAccountCredentials() {
        StorageCredentials creds = credentials;
        //if  credentials is null, fallback to credentialsAccountKey and credentialsAccountName
        if (creds == null) {
            if (credentialsAccountKey != null && credentialsAccountName != null) {
                creds = new StorageCredentialsAccountAndKey(credentialsAccountName, credentialsAccountKey);
            }
        }
        return creds;

    }
}
