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

package org.apache.camel.test.infra.ftp.services.embedded;

import java.io.File;

import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;

public class FtpsEmbeddedService extends FtpEmbeddedService {
    public FtpsEmbeddedService(EmbeddedConfigurationBuilder embeddedConfigurationBuilder) {
        super(EmbeddedConfigurationBuilder.defaultFtpsConfigurationTemplate());
    }

    public FtpsEmbeddedService(EmbeddedConfiguration.SecurityConfiguration securityConfiguration) {
        super(EmbeddedConfigurationBuilder.defaultFtpsConfigurationTemplate().withSecurityConfiguration(securityConfiguration));
    }

    @Deprecated
    public FtpsEmbeddedService(boolean useImplicit, String authValue, boolean clientAuth) {
        super(EmbeddedConfigurationBuilder.defaultFtpsConfigurationTemplate());

    }

    @Override
    protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
        FtpServerFactory serverFactory = super.createFtpServerFactory(embeddedConfiguration);

        ListenerFactory listenerFactory = new ListenerFactory(serverFactory.getListener(DEFAULT_LISTENER));
        listenerFactory.setPort(port);
        listenerFactory.setImplicitSsl(embeddedConfiguration.getSecurityConfiguration().isUseImplicit());
        listenerFactory.setSslConfiguration(createSslConfiguration(embeddedConfiguration).createSslConfiguration());

        serverFactory.addListener(DEFAULT_LISTENER, listenerFactory.createListener());

        return serverFactory;
    }

    private SslConfigurationFactory createSslConfiguration(EmbeddedConfiguration embeddedConfiguration) {
        // NOTE: if you have trouble with SSL set the system property "javax.net.debug" to "all"

        SslConfigurationFactory sslConfigFactory = new SslConfigurationFactory();
        sslConfigFactory.setSslProtocol(embeddedConfiguration.getSecurityConfiguration().getAuthValue());

        sslConfigFactory.setKeystoreFile(new File(embeddedConfiguration.getKeyStore()));
        sslConfigFactory.setKeystoreType(embeddedConfiguration.getKeyStoreType());
        sslConfigFactory.setKeystoreAlgorithm(embeddedConfiguration.getKeyStoreAlgorithm());
        sslConfigFactory.setKeystorePassword(embeddedConfiguration.getKeyStorePassword());
        sslConfigFactory.setKeyPassword(embeddedConfiguration.getKeyStorePassword());

        sslConfigFactory.setClientAuthentication(embeddedConfiguration.getSecurityConfiguration().getAuthValue());

        if (embeddedConfiguration.getSecurityConfiguration().isClientAuth()) {
            sslConfigFactory.setTruststoreFile(new File(embeddedConfiguration.getKeyStore()));
            sslConfigFactory.setTruststoreType(embeddedConfiguration.getKeyStoreType());
            sslConfigFactory.setTruststoreAlgorithm(embeddedConfiguration.getKeyStoreAlgorithm());
            sslConfigFactory.setTruststorePassword(embeddedConfiguration.getKeyStorePassword());
        }

        return sslConfigFactory;
    }
}
