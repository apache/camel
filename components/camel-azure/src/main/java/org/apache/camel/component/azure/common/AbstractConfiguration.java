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
package org.apache.camel.component.azure.common;

import com.microsoft.azure.storage.StorageCredentials;
import org.apache.camel.spi.UriParam;

public abstract class AbstractConfiguration implements Cloneable {

    @UriParam
    private StorageCredentials credentials;
    
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
}