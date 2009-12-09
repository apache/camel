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
package org.apache.camel.component.file.remote;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;

/**
 * FTP Secure (FTP over SSL/TLS) endpoint
 * 
 * @version $Revision$
 * @author muellerc
 */
public class FtpsEndpoint extends FtpEndpoint<FTPFile> {
    
    protected Map<String, Object> ftpClientKeyStoreParameters;
    protected Map<String, Object> ftpClientTrustStoreParameters;

    public FtpsEndpoint() {
    }

    public FtpsEndpoint(String uri, RemoteFileComponent<FTPFile> remoteFileComponent, RemoteFileConfiguration configuration) {
        super(uri, remoteFileComponent, configuration);
    }

    @Override
    public String getScheme() {
        return getFtpsConfiguration().getProtocol();
    }
    
    /**
     * Create the FTPS client.
     */
    protected FTPClient createFtpClient() throws Exception {
        FTPSClient client = new FTPSClient(getFtpsConfiguration().getSecurityProtocol(),
                                           getFtpsConfiguration().isImplicit());
        
        if (ftpClientKeyStoreParameters != null) {
            String type = (ftpClientKeyStoreParameters.containsKey("type"))
                    ? (String) ftpClientKeyStoreParameters.get("type") : KeyStore.getDefaultType();
            String file = (String) ftpClientKeyStoreParameters.get("file");
            String password = (String) ftpClientKeyStoreParameters.get("password");
            String algorithm = (ftpClientKeyStoreParameters.containsKey("algorithm"))
                    ? (String) ftpClientKeyStoreParameters.get("algorithm")
                    : KeyManagerFactory.getDefaultAlgorithm();
            String keyPassword = (String) ftpClientKeyStoreParameters.get("keyPassword");
            
            KeyStore keyStore = KeyStore.getInstance(type);
            FileInputStream keyStoreFileInputStream = new FileInputStream(new File(file));
            try {
                keyStore.load(keyStoreFileInputStream, password.toCharArray());
            } finally {
                ObjectHelper.close(keyStoreFileInputStream, "keyStore", log);
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
                    ? (String) ftpClientTrustStoreParameters.get("algorithm")
                    : TrustManagerFactory.getDefaultAlgorithm();
                    
            KeyStore trustStore = KeyStore.getInstance(type);
            FileInputStream trustStoreFileInputStream = new FileInputStream(new File(file));
            try {
                trustStore.load(trustStoreFileInputStream, password.toCharArray());
            } finally {
                ObjectHelper.close(trustStoreFileInputStream, "trustStore", log);
            }

            TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
            trustMgrFactory.init(trustStore);
            
            client.setTrustManager(trustMgrFactory.getTrustManagers()[0]);
        }
        
        return client;
    }

    /**
     * Returns the FTPSClient. This method exists only for convenient.
     * 
     * @return ftpsClient
     */
    public FTPSClient getFtpsClient() {
        return (FTPSClient) getFtpClient();
    }
    
    /**
     * Returns the FtpsConfiguration. This method exists only for convenient.
     * 
     * @return ftpsConfiguration
     */
    public FtpsConfiguration getFtpsConfiguration() {
        return (FtpsConfiguration) getConfiguration();
    }

    /**
     * Set the key store parameters
     */
    public void setFtpClientKeyStoreParameters(Map<String, Object> param) {
        this.ftpClientKeyStoreParameters = param;
    }

    /**
     * Set the trust store parameters
     */
    public void setFtpClientTrustStoreParameters(Map<String, Object> param) {
        this.ftpClientTrustStoreParameters = param;
    }

}