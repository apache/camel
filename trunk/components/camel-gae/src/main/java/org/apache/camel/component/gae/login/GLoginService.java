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
 * Interface to login services. 
 */
public interface GLoginService {

    /**
     * Authenticates a user and stores the authentication token to
     * {@link GLoginData#setAuthenticationToken(String)} (only if needed by
     * {@link #authorize(GLoginData)}).
     * 
     * @param data authentication input data and response data (authentication token) container.
     */
    void authenticate(GLoginData data) throws Exception;

    /**
     * Authorizes access to an application and stores an authorization cookie to
     * {@link GLoginData#setAuthorizationCookie(String)}.
     * 
     * @param data authentication input data and response data (authorization cookie) container.
     */
    void authorize(GLoginData data) throws Exception;

}
