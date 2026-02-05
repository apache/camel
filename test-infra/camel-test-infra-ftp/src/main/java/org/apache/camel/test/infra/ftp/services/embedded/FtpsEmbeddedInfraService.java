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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.ftp.services.FtpInfraService;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InfraService(service = FtpInfraService.class,
              description = "Embedded FTPS Server",
              serviceAlias = { "ftps" })
public class FtpsEmbeddedInfraService extends FtpEmbeddedInfraService {

    private static final Logger LOG = LoggerFactory.getLogger(FtpsEmbeddedInfraService.class);

    /**
     * Use a default constructor with a default security configuration for camel jbang
     */
    public FtpsEmbeddedInfraService() {
        super(EmbeddedConfigurationBuilder.defaultFtpsConfigurationTemplate()
                .withSecurityConfiguration(false, "TLSv1.3", true));
    }

    public FtpsEmbeddedInfraService(EmbeddedConfiguration.SecurityConfiguration securityConfiguration) {
        super(EmbeddedConfigurationBuilder.defaultFtpsConfigurationTemplate().withSecurityConfiguration(securityConfiguration));
    }

    @Deprecated
    public FtpsEmbeddedInfraService(boolean useImplicit, String authValue, boolean clientAuth) {
        super(EmbeddedConfigurationBuilder.defaultFtpsConfigurationTemplate());

    }

    @Override
    protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
        FtpServerFactory serverFactory = super.createFtpServerFactory(embeddedConfiguration);

        ListenerFactory listenerFactory = new ListenerFactory(serverFactory.getListener(DEFAULT_LISTENER));
        // If port was already assigned (restart scenario), reuse it; otherwise get a new one
        if (port > 0) {
            listenerFactory.setPort(port);
        } else {
            listenerFactory.setPort(ContainerEnvironmentUtil.getConfiguredPortOrRandom());
        }
        listenerFactory.setImplicitSsl(embeddedConfiguration.getSecurityConfiguration().isUseImplicit());
        listenerFactory.setSslConfiguration(createSslConfiguration(embeddedConfiguration).createSslConfiguration());

        serverFactory.addListener(DEFAULT_LISTENER, listenerFactory.createListener());

        return serverFactory;
    }

    private SslConfigurationFactory createSslConfiguration(EmbeddedConfiguration embeddedConfiguration) {
        // NOTE: if you have trouble with SSL set the system property "javax.net.debug" to "all"

        SslConfigurationFactory sslConfigFactory = new SslConfigurationFactory();
        sslConfigFactory.setSslProtocol(embeddedConfiguration.getSecurityConfiguration().getAuthValue());

        File keystoreFile = resolveKeystoreFile(embeddedConfiguration.getKeyStore());
        sslConfigFactory.setKeystoreFile(keystoreFile);
        sslConfigFactory.setKeystoreType(embeddedConfiguration.getKeyStoreType());
        sslConfigFactory.setKeystoreAlgorithm(embeddedConfiguration.getKeyStoreAlgorithm());
        sslConfigFactory.setKeystorePassword(embeddedConfiguration.getKeyStorePassword());
        sslConfigFactory.setKeyPassword(embeddedConfiguration.getKeyStorePassword());

        sslConfigFactory.setClientAuthentication(embeddedConfiguration.getSecurityConfiguration().getAuthValue());

        if (embeddedConfiguration.getSecurityConfiguration().isClientAuth()) {
            sslConfigFactory.setTruststoreFile(keystoreFile);
            sslConfigFactory.setTruststoreType(embeddedConfiguration.getKeyStoreType());
            sslConfigFactory.setTruststoreAlgorithm(embeddedConfiguration.getKeyStoreAlgorithm());
            sslConfigFactory.setTruststorePassword(embeddedConfiguration.getKeyStorePassword());
        }

        return sslConfigFactory;
    }

    /**
     * Resolves the keystore file, trying file path first, then classpath resource.
     *
     * @param  configuredPath the configured keystore file path
     * @return                the resolved keystore file
     */
    private File resolveKeystoreFile(String configuredPath) {
        // First try the configured file path
        File keystoreFile = new File(configuredPath);
        if (keystoreFile.exists()) {
            LOG.debug("Using keystore file: {}", keystoreFile.getAbsolutePath());
            return keystoreFile;
        }

        // Fall back to classpath resource
        return extractKeystoreFromClasspath();
    }

    /**
     * Extracts the bundled keystore from classpath to a temporary file.
     *
     * @return the temporary file containing the keystore
     */
    private File extractKeystoreFromClasspath() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(EmbeddedConfiguration.BUNDLED_KEYSTORE_RESOURCE)) {
            if (is != null) {
                Path tempFile = Files.createTempFile("ftps-keystore-", ".jks");
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                tempFile.toFile().deleteOnExit();
                LOG.info("Using bundled keystore from classpath, extracted to: {}", tempFile);
                return tempFile.toFile();
            }
        } catch (IOException e) {
            LOG.warn("Failed to extract bundled keystore from classpath: {}", e.getMessage());
        }

        throw new IllegalStateException(
                "Keystore file not found and bundled keystore not available in classpath");
    }
}
