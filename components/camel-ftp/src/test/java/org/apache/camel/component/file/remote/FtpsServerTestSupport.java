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
package org.apache.camel.component.file.remote;

import java.io.File;
import java.security.NoSuchAlgorithmException;

import org.apache.camel.util.ObjectHelper;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract base class for unit testing using a secure FTP Server (over SSL/TLS)
 */
public abstract class FtpsServerTestSupport extends FtpServerTestSupport {

    protected static final String AUTH_VALUE_SSL = "SSLv3";
    protected static final String AUTH_VALUE_TLS = "TLSv1.2";

    protected static final File FTPSERVER_KEYSTORE = new File("./src/test/resources/server.jks");
    protected static final String FTPSERVER_KEYSTORE_PASSWORD = "password";

    private static final Logger LOG = LoggerFactory.getLogger(FtpsServerTestSupport.class);

    @Override
    protected FtpServerFactory createFtpServerFactory() throws Exception {
        try {
            return doCreateFtpServerFactory();
        } catch (Exception e) {
            // ignore if algorithm is not on the OS
            NoSuchAlgorithmException nsae = ObjectHelper.getException(NoSuchAlgorithmException.class, e);
            if (nsae != null) {
                String name = System.getProperty("os.name");
                String message = nsae.getMessage();
                LOG.warn("SunX509 is not avail on this platform [{}] Testing is skipped! Real cause: {}", name, message);

                return null;
            } else {
                // some other error then throw it so the test can fail
                throw e;
            }
        }
    }

    protected FtpServerFactory doCreateFtpServerFactory() throws Exception {
        assertTrue(FTPSERVER_KEYSTORE.exists());

        FtpServerFactory serverFactory = super.createFtpServerFactory();

        ListenerFactory listenerFactory = new ListenerFactory(serverFactory.getListener(DEFAULT_LISTENER));
        listenerFactory.setImplicitSsl(useImplicit());
        listenerFactory.setSslConfiguration(createSslConfiguration().createSslConfiguration());

        serverFactory.addListener(DEFAULT_LISTENER, listenerFactory.createListener());

        return serverFactory;
    }

    protected SslConfigurationFactory createSslConfiguration() {
        // comment in, if you have trouble with SSL
        // System.setProperty("javax.net.debug", "all");

        SslConfigurationFactory sslConfigFactory = new SslConfigurationFactory();
        sslConfigFactory.setSslProtocol(getAuthValue());

        sslConfigFactory.setKeystoreFile(FTPSERVER_KEYSTORE);
        sslConfigFactory.setKeystoreType("JKS");
        sslConfigFactory.setKeystoreAlgorithm("SunX509");
        sslConfigFactory.setKeystorePassword(FTPSERVER_KEYSTORE_PASSWORD);
        sslConfigFactory.setKeyPassword(FTPSERVER_KEYSTORE_PASSWORD);

        sslConfigFactory.setClientAuthentication(getClientAuth());

        if (Boolean.valueOf(getClientAuth())) {
            sslConfigFactory.setTruststoreFile(FTPSERVER_KEYSTORE);
            sslConfigFactory.setTruststoreType("JKS");
            sslConfigFactory.setTruststoreAlgorithm("SunX509");
            sslConfigFactory.setTruststorePassword(FTPSERVER_KEYSTORE_PASSWORD);
        }

        return sslConfigFactory;
    }

    /**
     * Set what client authentication level to use, supported values are "yes"
     * or "true" for required authentication, "want" for wanted authentication
     * and "false" or "none" for no authentication. Defaults to "none".
     * 
     * @return clientAuthReqd
     */
    protected abstract String getClientAuth();

    /**
     * Should listeners created by this factory automatically be in SSL mode
     * automatically or must the client explicitly request to use SSL
     */
    protected abstract boolean useImplicit();

    /**
     * Set the SSL protocol used for this channel. Supported values are "SSL"
     * and "TLS".
     */
    protected abstract String getAuthValue();
}
