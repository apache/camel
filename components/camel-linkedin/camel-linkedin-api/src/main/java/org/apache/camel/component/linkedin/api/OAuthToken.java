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
package org.apache.camel.component.linkedin.api;

/**
* LinkedIn OAuth Token
*/
public final class OAuthToken {

    private final String refreshToken;
    private final String accessToken;
    private long expiryTime;

    public OAuthToken(String refreshToken, String accessToken, long expiryTime) {
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.expiryTime = expiryTime;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    // package method for testing only
    void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }
}
