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

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.file.FileProcessStrategy;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.remote.strategy.FtpProcessStrategyFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.PropertiesHelper;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP Secure (FTP over SSL/TLS) Component.
 * <p/>
 * If desired, the JVM property <tt>-Djavax.net.debug=all</tt> can be used to
 * see wire-level SSL details.
 */
@Component("ftps")
@FileProcessStrategy(FtpProcessStrategyFactory.class)
public class FtpsComponent extends FtpComponent implements SSLContextParametersAware {

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public FtpsComponent() {
    }

    public FtpsComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<FTPFile> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String baseUri = getBaseUri(uri);

        // lets make sure we create a new configuration as each endpoint can
        // customize its own version
        // must pass on baseUri to the configuration (see above)
        FtpsConfiguration config = new FtpsConfiguration(new URI(baseUri));

        FtpUtils.ensureRelativeFtpDirectory(this, config);

        FtpsEndpoint endpoint = new FtpsEndpoint(uri, this, config);
        extractAndSetFtpClientKeyStoreParameters(parameters, endpoint);
        extractAndSetFtpClientTrustStoreParameters(parameters, endpoint);
        extractAndSetFtpClientConfigParameters(parameters, endpoint);
        extractAndSetFtpClientParameters(parameters, endpoint);

        if (endpoint.getSslContextParameters() == null) {
            endpoint.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    /**
     * Extract additional ftp client key store options from the parameters map
     * (parameters starting with 'ftpClient.keyStore.'). To remember these
     * parameters, we set them in the endpoint and we can use them when creating
     * a client.
     */
    protected void extractAndSetFtpClientKeyStoreParameters(Map<String, Object> parameters, FtpsEndpoint endpoint) {
        if (PropertiesHelper.hasProperties(parameters, "ftpClient.keyStore.")) {
            Map<String, Object> param = PropertiesHelper.extractProperties(parameters, "ftpClient.keyStore.");
            endpoint.setFtpClientKeyStoreParameters(param);
        }
    }

    /**
     * Extract additional ftp client trust store options from the parameters map
     * (parameters starting with 'ftpClient.trustStore.'). To remember these
     * parameters, we set them in the endpoint and we can use them when creating
     * a client.
     */
    protected void extractAndSetFtpClientTrustStoreParameters(Map<String, Object> parameters, FtpsEndpoint endpoint) {
        if (PropertiesHelper.hasProperties(parameters, "ftpClient.trustStore.")) {
            Map<String, Object> param = PropertiesHelper.extractProperties(parameters, "ftpClient.trustStore.");
            endpoint.setFtpClientTrustStoreParameters(param);
        }
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

}
