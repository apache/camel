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
package org.apache.camel.component.salesforce.api.utils;

import java.util.Arrays;

import org.eclipse.jetty.util.ssl.SslContextFactory;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static void adaptToIBMCipherNames(final SslContextFactory sslContextFactory) {
        //jetty client adds into excluded cipher suites all ciphers starting with SSL_
        //it makes sense for Oracle jdk, but in IBM jdk all ciphers starts with SSL_, even ciphers for TLS
        //see https://github.com/eclipse/jetty.project/issues/2921
        if (System.getProperty("java.vendor").contains("IBM")) {
            String[] excludedCiphersWithoutSSLExclusion = Arrays.stream(sslContextFactory.getExcludeCipherSuites())
                    .filter(cipher -> !cipher.equals("^SSL_.*$"))
                    .toArray(String[]::new);
            sslContextFactory.setExcludeCipherSuites(excludedCiphersWithoutSSLExclusion);
        }
    }
}
