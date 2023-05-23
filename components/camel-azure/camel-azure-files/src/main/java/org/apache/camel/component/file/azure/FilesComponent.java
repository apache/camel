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
package org.apache.camel.component.file.azure;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileComponent;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.component.PropertyConfigurerSupport;

import com.azure.storage.file.share.models.ShareFileItem;

/**
 * Azure Files Component
 */
@Component("azure-files")
public class FilesComponent extends RemoteFileComponent<ShareFileItem> {

    public FilesComponent() {
    }

    public FilesComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<ShareFileItem> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        String baseUri = getBaseUri(uri);

        // lets make sure we create a new configuration as each endpoint can
        // customize its own version
        // must pass on baseUri to the configuration (see above)
        FilesConfiguration config = new FilesConfiguration(new URI(baseUri));

        FtpUtils.ensureRelativeFtpDirectory(this, config);

        FilesEndpoint<ShareFileItem> answer = new FilesEndpoint<>(uri, this, config);

        return answer;
    }

    /**
     * Get the base uri part before the options as they can be non URI valid such as the expression using $ chars and
     * the URI constructor will regard $ as an illegal character and we don't want to enforce end users to to escape the
     * $ for the expression (file language)
     */
    protected String getBaseUri(String uri) {
        String baseUri = uri;
        if (uri.indexOf('?') != -1) {
            baseUri = uri.substring(0, uri.indexOf('?'));
        }
        return baseUri;
    }

    @Override
    protected void setProperties(Endpoint endpoint, Map<String, Object> parameters) throws Exception {
        Object siteCommand = parameters.remove("siteCommand");
        if (siteCommand != null) {
            String cmd = PropertyConfigurerSupport.property(getCamelContext(), String.class, siteCommand);
            if (EndpointHelper.isReferenceParameter(cmd)) {
                cmd = EndpointHelper.resolveReferenceParameter(getCamelContext(), cmd, String.class);
            }
            ((FilesEndpoint) endpoint).getConfiguration().setSiteCommand(cmd);
        }
        super.setProperties(endpoint, parameters);
    }

    @Override
    protected void afterPropertiesSet(GenericFileEndpoint<ShareFileItem> endpoint) throws Exception {
        // noop
    }
}
