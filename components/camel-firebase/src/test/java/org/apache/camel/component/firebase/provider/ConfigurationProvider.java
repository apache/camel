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
package org.apache.camel.component.firebase.provider;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.apache.camel.component.firebase.FirebaseConfig;

import static org.junit.Assert.assertNotNull;

/**
 * Provides the path of the configuration used to access Firebase.
 */
public final class ConfigurationProvider {

    private ConfigurationProvider() {
    }

    public static String createFirebaseConfigLink() throws URISyntaxException, UnsupportedEncodingException {
        URL url = Thread.currentThread().getContextClassLoader().getResource("firebase-admin-connection.json");
        assertNotNull(url);
        File f = new File(url.toURI());
        return URLEncoder.encode(f.getAbsolutePath(), "UTF-8");
    }

    public static FirebaseConfig createDemoConfig() throws IOException, URISyntaxException {
        FirebaseConfig firebaseConfig = new FirebaseConfig.Builder(String.format("https://%s", createDatabaseUrl()), createRootReference(),
                URLDecoder.decode(createFirebaseConfigLink(), "UTF-8")).build();
        firebaseConfig.init();
        return firebaseConfig;
    }

    public static String createRootReference() {
        return "server/saving-data";
    }

    public static String createDatabaseUrl() {
        return "gil-sample-app.firebaseio.com";
    }
}
