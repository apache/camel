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
package org.apache.camel.component.huaweicloud.smn.models;

public class ServiceKeys {
    /**
     * cloud service authentication key (AK)
     */
    private String authenticationKey;

    /**
     * cloud service secret key (SK)
     */
    private String secretKey;

    public ServiceKeys() {
    }

    public ServiceKeys(String authenticationKey, String secretKey) {
        this.authenticationKey = authenticationKey;
        this.secretKey = secretKey;
    }

    public ServiceKeys(String authenticationKey, String secretKey, String projectId) {
        this.authenticationKey = authenticationKey;
        this.secretKey = secretKey;
    }

    public String getAuthenticationKey() {
        return authenticationKey;
    }

    public void setAuthenticationKey(String authenticationKey) {
        this.authenticationKey = authenticationKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

}
