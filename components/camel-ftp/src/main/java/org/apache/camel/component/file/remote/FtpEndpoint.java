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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.component.file.remote.RemoteFileConfiguration.PathSeparator;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.PlatformHelper;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;

/**
 * The ftp component is used for uploading or downloading files from FTP servers.
 */
@UriEndpoint(scheme = "ftp", extendsScheme = "file", title = "FTP",
        syntax = "ftp:host:port/directoryName", alternativeSyntax = "ftp:username:password@host:port/directoryName",
        consumerClass = FtpConsumer.class, label = "file")
public class FtpEndpoint<T extends FTPFile> extends RemoteFileEndpoint<FTPFile> {
    protected int soTimeout;
    protected int dataTimeout;

    @UriParam
    protected FtpConfiguration configuration;
    @UriParam(label = "advanced")
    protected FTPClientConfig ftpClientConfig;
    @UriParam(label = "advanced", prefix = "ftpClientConfig.", multiValue = true)
    protected Map<String, Object> ftpClientConfigParameters;
    @UriParam(label = "advanced", prefix = "ftpClient.", multiValue = true)
    protected Map<String, Object> ftpClientParameters;
    @UriParam(label = "advanced")
    protected FTPClient ftpClient;

    public FtpEndpoint() {
    }

    public FtpEndpoint(String uri, RemoteFileComponent<FTPFile> component, FtpConfiguration configuration) {
        super(uri, component, configuration);
        this.configuration = configuration;
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

        // use configured buffer size which is larger and therefore faster (as the default is no buffer)
        if (getConfiguration().getReceiveBufferSize() > 0) {
            client.setBufferSize(getConfiguration().getReceiveBufferSize());
        }
        // set any endpoint configured timeouts
        if (getConfiguration().getConnectTimeout() > -1) {
            client.setConnectTimeout(getConfiguration().getConnectTimeout());
        }
        if (getConfiguration().getSoTimeout() > -1) {
            soTimeout = getConfiguration().getSoTimeout();
        }
        dataTimeout = getConfiguration().getTimeout();

        if (getConfiguration().getActivePortRange() != null) {
            // parse it as min-max
            String[] parts = getConfiguration().getActivePortRange().split("-");
            if (parts.length != 2) {
                throw new IllegalArgumentException("The option activePortRange should have syntax: min-max");
            }
            int min = getCamelContext().getTypeConverter().mandatoryConvertTo(int.class, parts[0]);
            int max = getCamelContext().getTypeConverter().mandatoryConvertTo(int.class, parts[1]);
            log.debug("Using active port range: {}-{}", min, max);
            client.setActivePortRange(min, max);
        }

        // then lookup ftp client parameters and set those
        if (ftpClientParameters != null) {
            Map<String, Object> localParameters = new HashMap<String, Object>(ftpClientParameters);
            // setting soTimeout has to be done later on FTPClient (after it has connected)
            Object timeout = localParameters.remove("soTimeout");
            if (timeout != null) {
                soTimeout = getCamelContext().getTypeConverter().convertTo(int.class, timeout);
            }
            // and we want to keep data timeout so we can log it later
            timeout = localParameters.remove("dataTimeout");
            if (timeout != null) {
                dataTimeout = getCamelContext().getTypeConverter().convertTo(int.class, dataTimeout);
            }
            setProperties(client, localParameters);
        }
        
        if (ftpClientConfigParameters != null) {
            // client config is optional so create a new one if we have parameter for it
            if (ftpClientConfig == null) {
                ftpClientConfig = new FTPClientConfig();
            }
            Map<String, Object> localConfigParameters = new HashMap<String, Object>(ftpClientConfigParameters);
            setProperties(ftpClientConfig, localConfigParameters);
        }

        if (dataTimeout > 0) {
            client.setDataTimeout(dataTimeout);
        }

        if (log.isDebugEnabled()) {
            log.debug("Created FTPClient [connectTimeout: {}, soTimeout: {}, dataTimeout: {}, bufferSize: {}"
                            + ", receiveDataSocketBufferSize: {}, sendDataSocketBufferSize: {}]: {}",
                    new Object[]{client.getConnectTimeout(), getSoTimeout(), dataTimeout, client.getBufferSize(),
                            client.getReceiveDataSocketBufferSize(), client.getSendDataSocketBufferSize(), client});
        }

        FtpOperations operations = new FtpOperations(client, getFtpClientConfig());
        operations.setEndpoint(this);
        return operations;
    }

    protected FTPClient createFtpClient() throws Exception {
        FTPClient client = new FTPClient();
        // If we're in an OSGI environment, set the parser factory to
        // OsgiParserFactory, because commons-net uses Class.forName in their
        // default ParserFactory
        if (isOsgi()) {
            ClassResolver cr = getCamelContext().getClassResolver();
            OsgiParserFactory opf = new OsgiParserFactory(cr);
            client.setParserFactory(opf);
        }
        return client;
    }

    private boolean isOsgi() {
        return PlatformHelper.isOsgiContext(getCamelContext());
    }

    @Override
    public FtpConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new FtpConfiguration();
        }
        return configuration;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("FtpConfiguration expected");
        }
        // need to set on both
        this.configuration = (FtpConfiguration) configuration;
        super.setConfiguration(configuration);
    }

    public FTPClient getFtpClient() {
        return ftpClient;
    }

    /**
     * To use a custom instance of FTPClient
     */
    public void setFtpClient(FTPClient ftpClient) {
        this.ftpClient = ftpClient;
    }

    public FTPClientConfig getFtpClientConfig() {
        return ftpClientConfig;
    }

    /**
     * To use a custom instance of FTPClientConfig to configure the FTP client the endpoint should use.
     */
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
        this.ftpClientConfigParameters = new HashMap<String, Object>(ftpClientConfigParameters);
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    /**
     * Sets the soTimeout on the FTP client.
     */
    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public int getDataTimeout() {
        return dataTimeout;
    }

    /**
     * Sets the data timeout on the FTP client.
     */
    public void setDataTimeout(int dataTimeout) {
        this.dataTimeout = dataTimeout;
    }

    @Override
    public char getFileSeparator() {
        // the regular ftp component should use the configured separator
        // as FTP servers may require you to use windows or unix style
        // and therefore you need to be able to control that
        PathSeparator pathSeparator = getConfiguration().getSeparator();
        switch (pathSeparator) {
        case Windows:
            return '\\';
        case UNIX:
            return '/';
        default:
            return super.getFileSeparator();
        }
    }
}
