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

package org.apache.camel.component.file.remote.services;

import java.io.File;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpsEmbeddedService extends FtpEmbeddedService {
    private static final Logger LOG = LoggerFactory.getLogger(FtpsEmbeddedService.class);
    private static final File FTPSERVER_KEYSTORE = new File("./src/test/resources/server.jks");
    private static final String FTPSERVER_KEYSTORE_PASSWORD = "password";

    private boolean useImplicit;
    private String authValue;
    private boolean clientAuth;

    public FtpsEmbeddedService(boolean useImplicit, String authValue, boolean clientAuth) {
        super();

        this.useImplicit = useImplicit;
        this.authValue = authValue;
        this.clientAuth = clientAuth;
    }

    @Override
    protected FtpServerFactory createFtpServerFactory() {
        FtpServerFactory serverFactory = super.createFtpServerFactory();

        ListenerFactory listenerFactory = new ListenerFactory(serverFactory.getListener(DEFAULT_LISTENER));
        listenerFactory.setImplicitSsl(useImplicit);
        listenerFactory.setSslConfiguration(createSslConfiguration().createSslConfiguration());

        serverFactory.addListener(DEFAULT_LISTENER, listenerFactory.createListener());

        return serverFactory;
    }

    protected SslConfigurationFactory createSslConfiguration() {
        // comment in, if you have trouble with SSL
        // System.setProperty("javax.net.debug", "all");

        SslConfigurationFactory sslConfigFactory = new SslConfigurationFactory();
        sslConfigFactory.setSslProtocol(authValue);

        sslConfigFactory.setKeystoreFile(FTPSERVER_KEYSTORE);
        sslConfigFactory.setKeystoreType("JKS");
        sslConfigFactory.setKeystoreAlgorithm("SunX509");
        sslConfigFactory.setKeystorePassword(FTPSERVER_KEYSTORE_PASSWORD);
        sslConfigFactory.setKeyPassword(FTPSERVER_KEYSTORE_PASSWORD);

        sslConfigFactory.setClientAuthentication(authValue);

        if (clientAuth) {
            sslConfigFactory.setTruststoreFile(FTPSERVER_KEYSTORE);
            sslConfigFactory.setTruststoreType("JKS");
            sslConfigFactory.setTruststoreAlgorithm("SunX509");
            sslConfigFactory.setTruststorePassword(FTPSERVER_KEYSTORE_PASSWORD);
        }

        return sslConfigFactory;
    }

    public static boolean hasRequiredAlgorithms() {
        LOG.info("Checking if the system has the required algorithms for the test execution");
        try {
            KeyManagerFactory.getInstance("SunX509");
            TrustManagerFactory.getInstance("SunX509");

            return true;
        } catch (NoSuchAlgorithmException e) {
            String name = System.getProperty("os.name");
            String message = e.getMessage();

            LOG.warn("SunX509 is not available on this platform [{}] Testing is skipped! Real cause: {}", name, message, e);
            return false;
        }
    }
}
