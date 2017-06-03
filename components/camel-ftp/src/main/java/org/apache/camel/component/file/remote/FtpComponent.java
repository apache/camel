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

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP Component
 */
public class FtpComponent extends RemoteFileComponent<FTPFile> {

    public FtpComponent() {
        setEndpointClass(FtpEndpoint.class);
    }

    public FtpComponent(CamelContext context) {
        super(context);
        setEndpointClass(FtpEndpoint.class);
    }

    @Override
    protected GenericFileEndpoint<FTPFile> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String baseUri = getBaseUri(uri);

        // lets make sure we create a new configuration as each endpoint can customize its own version
        // must pass on baseUri to the configuration (see above)
        FtpConfiguration config = new FtpConfiguration(new URI(baseUri));

        FtpUtils.ensureRelativeFtpDirectory(this, config);

        FtpEndpoint<FTPFile> answer = new FtpEndpoint<FTPFile>(uri, this, config);
        extractAndSetFtpClientConfigParameters(parameters, answer);
        extractAndSetFtpClientParameters(parameters, answer);

        return answer;
    }

    /**
     * Get the base uri part before the options as they can be non URI valid such as the expression using $ chars
     * and the URI constructor will regard $ as an illegal character and we don't want to enforce end users to
     * to escape the $ for the expression (file language)
     */
    protected String getBaseUri(String uri) {
        String baseUri = uri;
        if (uri.indexOf("?") != -1) {
            baseUri = uri.substring(0, uri.indexOf("?"));
        }
        return baseUri;
    }

    /**
     * Extract additional ftp client configuration options from the parameters map (parameters starting with 
     * 'ftpClientConfig.'). To remember these parameters, we set them in the endpoint and we can use them 
     * when creating a client.
     */
    protected void extractAndSetFtpClientConfigParameters(Map<String, Object> parameters, FtpEndpoint<FTPFile> answer) {
        if (IntrospectionSupport.hasProperties(parameters, "ftpClientConfig.")) {
            Map<String, Object> param = IntrospectionSupport.extractProperties(parameters, "ftpClientConfig.");
            answer.setFtpClientConfigParameters(param);
        }
    }
    
    /**
     * Extract additional ftp client options from the parameters map (parameters starting with 
     * 'ftpClient.'). To remember these parameters, we set them in the endpoint and we can use them 
     * when creating a client.
     */
    protected void extractAndSetFtpClientParameters(Map<String, Object> parameters, FtpEndpoint<FTPFile> answer) {
        if (IntrospectionSupport.hasProperties(parameters, "ftpClient.")) {
            Map<String, Object> param = IntrospectionSupport.extractProperties(parameters, "ftpClient.");
            answer.setFtpClientParameters(param);
        }
    }

    protected void afterPropertiesSet(GenericFileEndpoint<FTPFile> endpoint) throws Exception {
        // noop
    }
}