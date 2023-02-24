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
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.Category;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.IOHelper;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upload and download files to/from FTP servers supporting the FTPS protocol.
 */
@UriEndpoint(firstVersion = "2.2.0", scheme = "ftps", extendsScheme = "file", title = "FTPS",
             syntax = "ftps:host:port/directoryName", alternativeSyntax = "ftps:username:password@host:port/directoryName",
             category = { Category.FILE }, headersClass = FtpConstants.class)
@Metadata(excludeProperties = "appendChars,readLockIdempotentReleaseAsync,readLockIdempotentReleaseAsyncPoolSize,"
                              + "readLockIdempotentReleaseDelay,readLockIdempotentReleaseExecutorService,"
                              + "directoryMustExist,extendedAttributes,probeContentType,startingDirectoryMustExist,"
                              + "startingDirectoryMustHaveAccess,chmodDirectory,forceWrites,copyAndDeleteOnRenameFail,"
                              + "renameUsingCopy,synchronous")
@ManagedResource(description = "Managed FtpsEndpoint")
public class FtpsEndpoint extends FtpEndpoint<FTPFile> {
    private static final Logger LOG = LoggerFactory.getLogger(FtpsEndpoint.class);

    @UriParam
    protected FtpsConfiguration configuration;
    @UriParam(label = "security")
    protected SSLContextParameters sslContextParameters;
    @UriParam(label = "security", prefix = "ftpClient.keyStore.", multiValue = true)
    protected Map<String, Object> ftpClientKeyStoreParameters;
    @UriParam(label = "security", prefix = "ftpClient.trustStore.", multiValue = true)
    protected Map<String, Object> ftpClientTrustStoreParameters;

    public FtpsEndpoint() {
    }

    public FtpsEndpoint(String uri, RemoteFileComponent<FTPFile> remoteFileComponent, FtpsConfiguration configuration) {
        super(uri, remoteFileComponent, configuration);
        this.configuration = configuration;
    }

    @Override
    public FtpsConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new FtpsConfiguration();
        }
        return configuration;
    }

    @Override
    public String getScheme() {
        return getFtpsConfiguration().getProtocol();
    }

    @Override
    protected FTPClient createFtpClient() throws Exception {
        FTPSClient client;

        if (sslContextParameters != null) {
            SSLContext context = sslContextParameters.createSSLContext(getCamelContext());

            client = new FTPSClient(getFtpsConfiguration().isImplicit(), context);

            // The FTPSClient tries to manage the following SSLSocket related
            // configuration options
            // on its own based on internal configuration options. FTPSClient
            // does not lend itself
            // to subclassing for the purpose of overriding this behavior
            // (private methods, fields, etc.).
            // As such, we create a socket (preconfigured by
            // SSLContextParameters) from the context
            // we gave to FTPSClient and then setup FTPSClient to reuse the
            // already configured configuration
            // from the socket for all future sockets it creates. Not sexy and a
            // little brittle, but it works.
            SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
            client.setEnabledCipherSuites(socket.getEnabledCipherSuites());
            client.setEnabledProtocols(socket.getEnabledProtocols());
            client.setNeedClientAuth(socket.getNeedClientAuth());
            client.setWantClientAuth(socket.getWantClientAuth());
            client.setEnabledSessionCreation(socket.getEnableSessionCreation());
        } else {
            client = new FTPSClient(getFtpsConfiguration().getSecurityProtocol(), getFtpsConfiguration().isImplicit());

            if (ftpClientKeyStoreParameters != null) {
                String type = (ftpClientKeyStoreParameters.containsKey("type"))
                        ? (String) ftpClientKeyStoreParameters.get("type") : KeyStore.getDefaultType();
                String file = (String) ftpClientKeyStoreParameters.get("file");
                String password = (String) ftpClientKeyStoreParameters.get("password");
                String algorithm = (ftpClientKeyStoreParameters.containsKey("algorithm"))
                        ? (String) ftpClientKeyStoreParameters.get("algorithm") : KeyManagerFactory.getDefaultAlgorithm();
                String keyPassword = (String) ftpClientKeyStoreParameters.get("keyPassword");

                KeyStore keyStore = KeyStore.getInstance(type);
                FileInputStream keyStoreFileInputStream = new FileInputStream(new File(file));
                try {
                    keyStore.load(keyStoreFileInputStream, password.toCharArray());
                } finally {
                    IOHelper.close(keyStoreFileInputStream, "keyStore", LOG);
                }

                KeyManagerFactory keyMgrFactory = KeyManagerFactory.getInstance(algorithm);
                keyMgrFactory.init(keyStore, keyPassword.toCharArray());
                client.setNeedClientAuth(true);
                client.setKeyManager(keyMgrFactory.getKeyManagers()[0]);
            }

            if (ftpClientTrustStoreParameters != null) {
                String type = (ftpClientTrustStoreParameters.containsKey("type"))
                        ? (String) ftpClientTrustStoreParameters.get("type") : KeyStore.getDefaultType();
                String file = (String) ftpClientTrustStoreParameters.get("file");
                String password = (String) ftpClientTrustStoreParameters.get("password");
                String algorithm = (ftpClientTrustStoreParameters.containsKey("algorithm"))
                        ? (String) ftpClientTrustStoreParameters.get("algorithm") : TrustManagerFactory.getDefaultAlgorithm();

                KeyStore trustStore = KeyStore.getInstance(type);
                FileInputStream trustStoreFileInputStream = new FileInputStream(new File(file));
                try {
                    trustStore.load(trustStoreFileInputStream, password.toCharArray());
                } finally {
                    IOHelper.close(trustStoreFileInputStream, "trustStore", LOG);
                }

                TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
                trustMgrFactory.init(trustStore);

                client.setTrustManager(trustMgrFactory.getTrustManagers()[0]);
            }
        }

        return client;
    }

    @Override
    public RemoteFileOperations<FTPFile> createRemoteFileOperations() throws Exception {
        // configure ftp client
        FTPSClient client = getFtpsClient();

        if (client == null) {
            // must use a new client if not explicit configured to use a custom
            // client
            client = (FTPSClient) createFtpClient();
        }

        // use configured buffer size which is larger and therefore faster (as
        // the default is no buffer)
        if (getBufferSize() > 0) {
            client.setBufferSize(getBufferSize());
        }
        // set any endpoint configured timeouts
        if (getConfiguration().getConnectTimeout() > -1) {
            client.setConnectTimeout(getConfiguration().getConnectTimeout());
        }
        if (getConfiguration().getSoTimeout() > -1) {
            soTimeout = getConfiguration().getSoTimeout();
        }
        dataTimeout = getConfiguration().getTimeout();

        if (ftpClientParameters != null) {
            Map<String, Object> localParameters = new HashMap<>(ftpClientParameters);
            // setting soTimeout has to be done later on FTPClient (after it has
            // connected)
            Object timeout = localParameters.remove("soTimeout");
            if (timeout != null) {
                soTimeout = getCamelContext().getTypeConverter().convertTo(int.class, timeout);
            }
            // and we want to keep data timeout so we can log it later
            timeout = localParameters.remove("dataTimeout");
            if (timeout != null) {
                dataTimeout = getCamelContext().getTypeConverter().convertTo(int.class, timeout);
            }
            setProperties(client, localParameters);
        }

        if (ftpClientConfigParameters != null) {
            // client config is optional so create a new one if we have
            // parameter for it
            if (ftpClientConfig == null) {
                ftpClientConfig = new FTPClientConfig();
            }
            Map<String, Object> localConfigParameters = new HashMap<>(ftpClientConfigParameters);
            setProperties(ftpClientConfig, localConfigParameters);
        }

        if (dataTimeout > 0) {
            client.setDataTimeout(dataTimeout);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created FTPSClient[connectTimeout: {}, soTimeout: {}, dataTimeout: {}, bufferSize: {}"
                      + ", receiveDataSocketBufferSize: {}, sendDataSocketBufferSize: {}]: {}",
                    client.getConnectTimeout(), getSoTimeout(), dataTimeout, client.getBufferSize(),
                    client.getReceiveDataSocketBufferSize(), client.getSendDataSocketBufferSize(), client);
        }

        FtpsOperations operations = new FtpsOperations(client, getFtpClientConfig());
        operations.setEndpoint(this);
        return operations;
    }

    /**
     * Returns the FTPSClient. This method exists only for convenient.
     */
    public FTPSClient getFtpsClient() {
        return (FTPSClient) getFtpClient();
    }

    /**
     * Returns the FtpsConfiguration. This method exists only for convenient.
     */
    public FtpsConfiguration getFtpsConfiguration() {
        return getConfiguration();
    }

    public Map<String, Object> getFtpClientKeyStoreParameters() {
        return ftpClientKeyStoreParameters;
    }

    /**
     * Set the key store parameters
     */
    public void setFtpClientKeyStoreParameters(Map<String, Object> param) {
        this.ftpClientKeyStoreParameters = param;
    }

    public Map<String, Object> getFtpClientTrustStoreParameters() {
        return ftpClientTrustStoreParameters;
    }

    /**
     * Set the trust store parameters
     */
    public void setFtpClientTrustStoreParameters(Map<String, Object> param) {
        this.ftpClientTrustStoreParameters = param;
    }

    /**
     * Gets the JSSE configuration that overrides any settings in {@link FtpsEndpoint#ftpClientKeyStoreParameters},
     * {@link #ftpClientTrustStoreParameters}, and {@link FtpsConfiguration#getSecurityProtocol()}.
     */
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Gets the JSSE configuration that overrides any settings in {@link FtpsEndpoint#ftpClientKeyStoreParameters},
     * {@link #ftpClientTrustStoreParameters}, and {@link FtpsConfiguration#getSecurityProtocol()}.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

}
