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
package org.apache.camel.commands.jolokia;

import org.jolokia.client.BasicAuthenticator;
import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.J4pClientBuilderFactory;

/**
 * A factory to create a {@link org.jolokia.client.J4pClient} jolokia client that connects to a remote JVM.
 */
public final class JolokiaClientFactory {

    private JolokiaClientFactory() {
    }

    public static J4pClient createJolokiaClient(String jolokiaUrl, String username, String password) {
        J4pClientBuilder builder = J4pClientBuilderFactory.url(jolokiaUrl);
        boolean auth = false;
        if (isNotEmpty(username)) {
            builder = builder.user(username);
            auth = true;
        }
        if (isNotEmpty(password)) {
            builder = builder.password(password);
            auth = true;
        }
        if (auth) {
            builder = builder.authenticator(new BasicAuthenticator(true));
        }
        return builder.build();
    }

    private static boolean isNotEmpty(String text) {
        return text != null && !text.isEmpty();
    }

}
