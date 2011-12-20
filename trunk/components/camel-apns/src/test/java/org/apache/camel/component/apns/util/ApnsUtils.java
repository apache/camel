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
package org.apache.camel.component.apns.util;

import java.io.InputStream;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.notnoop.apns.internal.ApnsFeedbackParsingUtilsAcessor;
import com.notnoop.apns.internal.Utilities;
import com.notnoop.apns.utils.ApnsServerStub;
import com.notnoop.apns.utils.FixedCertificates;

import org.apache.camel.CamelContext;
import org.apache.camel.component.apns.factory.ApnsServiceFactory;

public final class ApnsUtils {

    private static Random random = new Random();

    private ApnsUtils() {
        super();
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
        SSLContext context = Utilities.newSSLContext(stream, FixedCertificates.SERVER_PASSWD, 
                                                     "PKCS12", getAlgorithm());

        
        ApnsServerStub server = new ApnsServerStub(
                context.getServerSocketFactory(),
                gatePort, feedPort);
        server.start();
        return server;
    }
    
    public static String getAlgorithm() {
        List<String> keys = new LinkedList<String>();
        List<String> trusts = new LinkedList<String>();
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
    
    public static SSLContext clientContext() throws Exception {
        InputStream stream = ClassLoader.getSystemResourceAsStream(FixedCertificates.CLIENT_STORE);
        SSLContext context = Utilities.newSSLContext(stream, 
                                                     FixedCertificates.CLIENT_PASSWD,
                                                     "PKCS12",
                                                     getAlgorithm());
        context.init(null, new TrustManager[] {new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            
        }}, new SecureRandom());
        return context;
    }
    
    public static ApnsServiceFactory createDefaultTestConfiguration(CamelContext camelContext) 
        throws Exception {
        ApnsServiceFactory apnsServiceFactory = new ApnsServiceFactory(camelContext);

        apnsServiceFactory.setFeedbackHost(FixedCertificates.TEST_HOST);
        apnsServiceFactory.setFeedbackPort(FixedCertificates.TEST_FEEDBACK_PORT);
        apnsServiceFactory.setGatewayHost(FixedCertificates.TEST_HOST);
        apnsServiceFactory.setGatewayPort(FixedCertificates.TEST_GATEWAY_PORT);
        // apnsServiceFactory.setCertificatePath("classpath:/" +
        // FixedCertificates.CLIENT_STORE);
        // apnsServiceFactory.setCertificatePassword(FixedCertificates.CLIENT_PASSWD);
        apnsServiceFactory.setSslContext(clientContext());
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
