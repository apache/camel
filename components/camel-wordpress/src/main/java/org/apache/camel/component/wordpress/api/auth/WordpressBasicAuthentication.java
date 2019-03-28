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
package org.apache.camel.component.wordpress.api.auth;

import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic Authentication implementation for Wordpress authentication mechanism. Should be used only on tested environments due to lack of security. Be aware that credentials will be passed over each
 * request to your server.
 * <p/>
 * On environments without non HTTPS this a high security risk.
 * <p/>
 * To this implementation work, the <a href="https://github.com/WP-API/Basic-Auth">Basic Authentication Plugin</a> must be installed into the Wordpress server.
 */
public class WordpressBasicAuthentication extends BaseWordpressAuthentication {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseWordpressAuthentication.class);

    public WordpressBasicAuthentication() {
    }

    public WordpressBasicAuthentication(String username, String password) {
        super(username, password);
    }

    /**
     * HTTP Basic Authentication configuration over CXF {@link ClientConfiguration}.
     * 
     * @see <a href= "http://cxf.apache.org/docs/jax-rs-client-api.html#JAX-RSClientAPI-ClientsandAuthentication">CXF Clients and Authentication</a>
     */
    @Override
    public void configureAuthentication(Object api) {
        if (isCredentialsSet()) {
            final String authorizationHeader = String.format("Basic %s", Base64Utility.encode(String.format("%s:%s", this.username, this.password).getBytes()));
            LOGGER.info("Credentials set for user {}", username);
            WebClient.client(api).header("Authorization", authorizationHeader);
        } else {
            LOGGER.warn("Credentials not set because username or password are empty.");
        }
    }

}
