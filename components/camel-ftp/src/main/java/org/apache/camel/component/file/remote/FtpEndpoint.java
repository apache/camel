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

import java.util.Map;

import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP endpoint
 */
public class FtpEndpoint<T extends FTPFile> extends RemoteFileEndpoint<FTPFile> {

    protected FTPClient ftpClient;
    protected FTPClientConfig ftpClientConfig;
    protected Map<String, Object> ftpClientParameters;
    protected Map<String, Object> ftpClientConfigParameters;
    protected int soTimeout;
    protected int dataTimeout;

    public FtpEndpoint() {
        super();
    }

    public FtpEndpoint(String uri, RemoteFileComponent<FTPFile> component, RemoteFileConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public String getScheme() {
        return "ftp";
    }

    @Override
    protected RemoteFileConsumer<FTPFile> buildConsumer(Processor processor) {
        try {
            return new FtpConsumer(this, processor, createRemoteFileOperations());
        } catch (Exception e) {
            throw new FailedToCreateConsumerException(this, e);
        }
    }

    protected GenericFileProducer<FTPFile> buildProducer() {
        try {
            return new RemoteFileProducer<FTPFile>(this, createRemoteFileOperations());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(this, e);
        }
    }
    
    public RemoteFileOperations<FTPFile> createRemoteFileOperations() throws Exception {
        // configure ftp client
        FTPClient client = ftpClient;
        
        if (client == null) {
            // must use a new client if not explicit configured to use a custom client
            client = createFtpClient();
        }

        // set any endpoint configured timeouts
        if (getConfiguration().getConnectTimeout() > -1) {
            client.setConnectTimeout(getConfiguration().getConnectTimeout());
        }
        if (getConfiguration().getSoTimeout() > -1) {
            soTimeout = getConfiguration().getSoTimeout();
        }
        dataTimeout = getConfiguration().getTimeout();

        // then lookup ftp client parameters and set those
        if (ftpClientParameters != null) {
            // setting soTimeout has to be done later on FTPClient (after it has connected)
            Object timeout = ftpClientParameters.remove("soTimeout");
            if (timeout != null) {
                soTimeout = getCamelContext().getTypeConverter().convertTo(int.class, timeout);
            }
            // and we want to keep data timeout so we can log it later
            timeout = ftpClientParameters.remove("dataTimeout");
            if (timeout != null) {
                dataTimeout = getCamelContext().getTypeConverter().convertTo(int.class, dataTimeout);
            }
            IntrospectionSupport.setProperties(client, ftpClientParameters);
        }
        
        if (ftpClientConfigParameters != null) {
            // client config is optional so create a new one if we have parameter for it
            if (ftpClientConfig == null) {
                ftpClientConfig = new FTPClientConfig();
            }
            IntrospectionSupport.setProperties(ftpClientConfig, ftpClientConfigParameters);
        }

        if (dataTimeout > 0) {
            client.setDataTimeout(dataTimeout);
        }

        if (log.isDebugEnabled()) {
            log.debug("Created FTPClient [connectTimeout: {}, soTimeout: {}, dataTimeout: {}]: {}", 
                    new Object[]{client.getConnectTimeout(), getSoTimeout(), dataTimeout, client});
        }

        FtpOperations operations = new FtpOperations(client, getFtpClientConfig());
        operations.setEndpoint(this);
        return operations;
    }

    protected FTPClient createFtpClient() throws Exception {
        return new FTPClient();
    }

    @Override
    public FtpConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new FtpConfiguration();
        }
        return (FtpConfiguration)configuration;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        setConfiguration((FtpConfiguration)configuration);
    }

    public void setConfiguration(FtpConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("FtpConfiguration expected");
        }
        this.configuration = configuration;
    }

    public FTPClient getFtpClient() {
        return ftpClient;
    }

    public void setFtpClient(FTPClient ftpClient) {
        this.ftpClient = ftpClient;
    }

    public FTPClientConfig getFtpClientConfig() {
        return ftpClientConfig;
    }

    public void setFtpClientConfig(FTPClientConfig ftpClientConfig) {
        this.ftpClientConfig = ftpClientConfig;
    }

    /**
     * Used by FtpComponent to provide additional parameters for the FTPClient
     */
    void setFtpClientParameters(Map<String, Object> ftpClientParameters) {
        this.ftpClientParameters = ftpClientParameters;
    }

    /**
     * Used by FtpComponent to provide additional parameters for the FTPClientConfig
     */
    void setFtpClientConfigParameters(Map<String, Object> ftpClientConfigParameters) {
        this.ftpClientConfigParameters = ftpClientConfigParameters;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    /**
     * Sets the soTimeout option.
     * <p/>
     * Used by FTPClient
     */
    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }
}
