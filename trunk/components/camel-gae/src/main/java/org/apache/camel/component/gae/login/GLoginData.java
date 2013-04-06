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
package org.apache.camel.component.gae.login;

/**
 * Container for login request and response data. 
 */
public class GLoginData {

    private String hostName;
    private String clientName;
    private String userName;
    private String password;
    private int devPort;
    private boolean devAdmin;
    private boolean devMode;
    private String authenticationToken;
    private String authorizationCookie;

    /**
     * @see GLoginEndpoint#getHostName()
     * @see GLoginBinding#GLOGIN_HOST_NAME
     */
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    /**
     * @see GLoginBinding#GLOGIN_USER_NAME
     */
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * @see GLoginBinding#GLOGIN_PASSWORD
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    
    /**
     * @see GLoginEndpoint#getDevPort()
     */
    public int getDevPort() {
        return devPort;
    }

    public void setDevPort(int devPort) {
        this.devPort = devPort;
    }

    public boolean isDevAdmin() {
        return devAdmin;
    }

    public void setDevAdmin(boolean devAdmin) {
        this.devAdmin = devAdmin;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public String getAuthenticationToken() {
        return authenticationToken;
    }

    /**
     * @see GLoginBinding#GLOGIN_TOKEN
     */
    public void setAuthenticationToken(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }

    public String getAuthorizationCookie() {
        return authorizationCookie;
    }

    /**
     * @see GLoginBinding#GLOGIN_COOKIE
     */
    public void setAuthorizationCookie(String authorizationCookie) {
        this.authorizationCookie = authorizationCookie;
    }
    
}
