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

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;

import com.google.gdata.client.GoogleAuthTokenFactory;
import com.google.gdata.util.AuthenticationException;

/**
 * Implements the interactions with Google's authentication and authorization
 * services. If the endpoint is configured to run in development mode the
 * authentication and authorization services of the development server are used.
 */
public class GLoginServiceImpl implements GLoginService {

    /**
     * Authenticates a user and stores the authentication token to
     * {@link GLoginData#setAuthenticationToken(String)}. If the endpoint is
     * configured to run in development mode this method simply returns without
     * any further action. 
     */
    public void authenticate(GLoginData data) throws AuthenticationException {
        if (data.isDevMode()) {
            return;
        }
        GoogleAuthTokenFactory factory = 
            new GoogleAuthTokenFactory("ah", data.getClientName(), null);
        String token = factory.getAuthToken(
            data.getUserName(), 
            data.getPassword(), 
            null, null, "ah", data.getClientName());
        data.setAuthenticationToken(token);       
    }

    /**
     * Dispatches authorization to {@link #authorizeDev(GLoginData)} if the
     * endpoint is configured to run in development mode, otherwise to
     * {@link #authorizeStd(GLoginData)}.
     */
    public void authorize(GLoginData data) throws Exception {
        if (data.isDevMode()) {
            authorizeDev(data);
        } else {
            authorizeStd(data);
        }
    }

    /**
     * Authorizes access to a development server and stores the resulting
     * authorization cookie to {@link GLoginData#setAuthorizationCookie(String)}
     * . Authorization in development mode doesn't require an authentication
     * token.
     */
    protected void authorizeDev(GLoginData data) throws Exception {
        String homeLocation = String.format("http://%s:%d", data.getHostName(), data.getDevPort());
        HttpURLConnection connection = createURLConnection(homeLocation + "/_ah/login", true);
        connection.connect();
        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(connection.getOutputStream()));
        writer.println(String.format("email=%s&isAdmin=%s&continue=%s",
            URLEncoder.encode(data.getUserName(), Charset.defaultCharset().name()), 
            data.isDevAdmin() ? "on" : "off", 
            URLEncoder.encode(homeLocation, Charset.defaultCharset().name())));
        writer.flush();
        data.setAuthorizationCookie(connection.getHeaderField("Set-Cookie"));
        connection.disconnect();        
    }
    
    /**
     * Authorizes access to a Google App Engine application and stores the
     * resulting authorization cookie to
     * {@link GLoginData#setAuthorizationCookie(String)}. This method requires
     * an authentication token from
     * {@link GLoginData#getAuthenticationToken()}.
     */
    protected void authorizeStd(GLoginData data) throws Exception {
        String url = String.format("https://%s/_ah/login?auth=%s",
            data.getHostName(), data.getAuthenticationToken());
        HttpURLConnection connection = createURLConnection(url, false);
        connection.connect();
        data.setAuthorizationCookie(connection.getHeaderField("Set-Cookie"));
        connection.disconnect();
    }
    
    private static HttpURLConnection createURLConnection(String url, boolean dev) throws Exception {
        // TODO: support usage of proxy (via endpoint parameters or GLoginData object)
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        if (dev) {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
        }
        return connection;
    }
    
}
