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
package org.apache.camel.component.zendesk.internal;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.zendesk.ZendeskConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zendesk.client.v2.Zendesk;

/**
 * ZendeskHelper.
 * 
 * <p>
 * Utility class for creating Zendesk API Connections
 */
public final class ZendeskHelper {

    private ZendeskHelper() {
        // hide utility class constructor
    }

    public static Zendesk create(ZendeskConfiguration configuration) {
        return new Zendesk.Builder(configuration.getServerUrl())
            .setUsername(configuration.getUsername())
            .setToken(configuration.getToken())
            .setOauthToken(configuration.getOauthToken())
            .setPassword(configuration.getPassword())
            .build();
    }
}
