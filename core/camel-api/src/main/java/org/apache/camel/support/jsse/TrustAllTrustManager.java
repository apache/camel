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
package org.apache.camel.support.jsse;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TrustManager that accepts all X509 certificates without any validation.
 *
 * <p>
 * WARNING: This implementation should only be used in a controlled environment, such as testing or development, as it
 * completely bypasses SSL certificate verification. Using this in production can expose the application to
 * man-in-the-middle attacks.
 * </p>
 */
public class TrustAllTrustManager implements X509TrustManager {

    private static final Logger LOG = LoggerFactory.getLogger(TrustAllTrustManager.class);

    public static final TrustAllTrustManager INSTANCE = new TrustAllTrustManager();

    private TrustAllTrustManager() {
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType) {
        LOG.debug("Trusting client certificate: {}", certs);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType) {
        LOG.debug("Trusting server certificate: {}", certs);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

}
