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
package org.apache.camel.component.mail;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  DummyTrustManager that accepts any given certificate - <b>NOT SECURE</b>.
 */
public class DummyTrustManager implements X509TrustManager {

    private static final Logger LOG = LoggerFactory.getLogger(DummyTrustManager.class);

    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // everything is trusted
        logCertificateChain("Client", chain);
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // everything is trusted
        logCertificateChain("Server", chain);
    }

    public X509Certificate[] getAcceptedIssuers() {
        // everything is trusted
        return new X509Certificate[0];
    }

    private static void logCertificateChain(String type, X509Certificate[] chain) {
        if (LOG.isDebugEnabled()) {
            for (X509Certificate certificate : chain) {
                LOG.debug("{} certificate is trusted: {}", type, certificate);
            }
        }
    }

}

