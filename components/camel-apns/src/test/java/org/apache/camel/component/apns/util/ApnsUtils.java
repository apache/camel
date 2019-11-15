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
package org.apache.camel.component.apns.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.notnoop.apns.internal.ApnsFeedbackParsingUtilsAcessor;
import com.notnoop.apns.internal.Utilities;
import com.notnoop.apns.utils.ApnsServerStub;
import com.notnoop.apns.utils.FixedCertificates;
import org.apache.camel.CamelContext;
import org.apache.camel.component.apns.factory.ApnsServiceFactory;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

public final class ApnsUtils {

    private static Random random = new Random();

    private ApnsUtils() {
    }

    public static byte[] createRandomDeviceTokenBytes() {
        byte[] deviceTokenBytes = new byte[32];
        random.nextBytes(deviceTokenBytes);

        return deviceTokenBytes;
    }

    public static String encodeHexToken(byte[] deviceTokenBytes) {
        String deviceToken = Utilities.encodeHex(deviceTokenBytes);

        return deviceToken;
    }
    
    public static ApnsServerStub prepareAndStartServer(int gatePort, int feedPort) {
        InputStream stream = ClassLoader.getSystemResourceAsStream(FixedCertificates.SERVER_STORE);
        SSLContext context = Utilities.newSSLContext(stream, FixedCertificates.SERVER_PASSWORD, 
                                                     "PKCS12", getAlgorithm());

        
        ApnsServerStub server = new ApnsServerStub(
                context.getServerSocketFactory(),
                gatePort, feedPort);
        server.start();
        return server;
    }
    
    public static String getAlgorithm() {
        List<String> keys = new LinkedList<>();
        List<String> trusts = new LinkedList<>();
        for (Provider p : Security.getProviders()) {
            for (Service s : p.getServices()) {
                if ("KeyManagerFactory".equals(s.getType())
                    && s.getAlgorithm().endsWith("509")) {
                    keys.add(s.getAlgorithm());
                } else if ("TrustManagerFactory".equals(s.getType())
                    && s.getAlgorithm().endsWith("509")) {
                    trusts.add(s.getAlgorithm());
                }
            }
        }
        keys.retainAll(trusts);
        return keys.get(0);
    }
    
    public static SSLContextParameters clientContext() throws Exception {
        final KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(ClassLoader.getSystemResource(FixedCertificates.CLIENT_STORE).toString());
        ksp.setType("PKCS12");

        final KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyStore(ksp);
        kmp.setKeyPassword(FixedCertificates.CLIENT_PASSWORD);
        kmp.setAlgorithm(getAlgorithm());

        final SSLContextParameters contextParameters = new SSLContextParameters();
        contextParameters.setKeyManagers(kmp);
        contextParameters.setTrustManagers(new TrustManagersParameters() {
            @Override
            public TrustManager[] createTrustManagers() throws GeneralSecurityException, IOException {
                return new TrustManager[] {new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                }};
            }
        });

        return contextParameters;
    }
    
    public static ApnsServiceFactory createDefaultTestConfiguration(CamelContext camelContext) 
        throws Exception {
        ApnsServiceFactory apnsServiceFactory = new ApnsServiceFactory(camelContext);

        apnsServiceFactory.setFeedbackHost(TestConstants.TEST_HOST);
        apnsServiceFactory.setFeedbackPort(TestConstants.TEST_FEEDBACK_PORT);
        apnsServiceFactory.setGatewayHost(TestConstants.TEST_HOST);
        apnsServiceFactory.setGatewayPort(TestConstants.TEST_GATEWAY_PORT);
        // apnsServiceFactory.setCertificatePath("classpath:/" +
        // FixedCertificates.CLIENT_STORE);
        // apnsServiceFactory.setCertificatePassword(FixedCertificates.CLIENT_PASSWD);
        apnsServiceFactory.setSslContextParameters(clientContext());
        return apnsServiceFactory;
    }

    public static byte[] generateFeedbackBytes(byte[] deviceTokenBytes) {
        byte[] feedbackBytes = ApnsFeedbackParsingUtilsAcessor.pack(
        /* time_t */new byte[] {0, 0, 0, 0},
        /* length */new byte[] {0, 32},
        /* device token */deviceTokenBytes);

        return feedbackBytes;
    }

}
