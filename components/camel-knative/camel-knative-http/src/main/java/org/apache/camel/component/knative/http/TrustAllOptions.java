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
package org.apache.camel.component.knative.http;

import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.function.Function;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;

/**
 * Trust options for trusting all client and server certificates. Do not use in production environments but primarily in
 * test environments. Implementation is heavily based on the Quarkus TLS registry implementation.
 */
public class TrustAllOptions implements TrustOptions {

    public static TrustAllOptions INSTANCE = new TrustAllOptions();

    private static final TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static final Provider PROVIDER = new Provider("", "0.0", "") {

    };

    private TrustAllOptions() {
        // Avoid direct instantiation.
    }

    @Override
    public TrustOptions copy() {
        return this;
    }

    @Override
    public TrustManagerFactory getTrustManagerFactory(Vertx vertx) {
        return new TrustManagerFactory(new TrustManagerFactorySpi() {
            @Override
            protected void engineInit(KeyStore keyStore) {
            }

            @Override
            protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
            }

            @Override
            protected TrustManager[] engineGetTrustManagers() {
                return new TrustManager[] { TRUST_ALL_MANAGER };
            }
        }, PROVIDER, "") {

        };
    }

    @Override
    public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) {
        return new Function<String, TrustManager[]>() {
            @Override
            public TrustManager[] apply(String name) {
                return new TrustManager[] { TRUST_ALL_MANAGER };
            }
        };
    }
}
