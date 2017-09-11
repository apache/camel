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
package org.apache.camel.component.mail;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.mail.search.SearchTerm;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Component for JavaMail.
 *
 * @version
 */
public class MailComponent extends UriEndpointComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced")
    private MailConfiguration configuration;
    @Metadata(label = "advanced")
    private ContentTypeResolver contentTypeResolver;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public MailComponent() {
        super(MailEndpoint.class);
    }

    public MailComponent(MailConfiguration configuration) {
        super(MailEndpoint.class);
        this.configuration = configuration;
    }

    public MailComponent(CamelContext context) {
        super(context, MailEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI url = new URI(uri);

        // must use copy as each endpoint can have different options
        MailConfiguration config = getConfiguration().copy();

        // only configure if we have a url with a known protocol
        config.configure(url);
        configureAdditionalJavaMailProperties(config, parameters);

        MailEndpoint endpoint = new MailEndpoint(uri, this, config);

        // special for search term bean reference
        Object searchTerm = getAndRemoveOrResolveReferenceParameter(parameters, "searchTerm", Object.class);
        if (searchTerm != null) {
            SearchTerm st;
            if (searchTerm instanceof SimpleSearchTerm) {
                // okay its a SimpleSearchTerm then lets convert that to SearchTerm
                st = MailConverters.toSearchTerm((SimpleSearchTerm) searchTerm, getCamelContext().getTypeConverter());
            } else {
                st = getCamelContext().getTypeConverter().mandatoryConvertTo(SearchTerm.class, searchTerm);
            }
            endpoint.setSearchTerm(st);
        }

        endpoint.setContentTypeResolver(contentTypeResolver);
        setProperties(endpoint.getConfiguration(), parameters);
        setProperties(endpoint, parameters);

        // special for searchTerm.xxx options
        Map<String, Object> sstParams = IntrospectionSupport.extractProperties(parameters, "searchTerm.");
        if (!sstParams.isEmpty()) {
            // use SimpleSearchTerm as POJO to store the configuration and then convert that to the actual SearchTerm
            SimpleSearchTerm sst = new SimpleSearchTerm();
            setProperties(sst, sstParams);
            SearchTerm st = MailConverters.toSearchTerm(sst, getCamelContext().getTypeConverter());
            endpoint.setSearchTerm(st);
        }

        // sanity check that we know the mail server
        ObjectHelper.notEmpty(config.getHost(), "host");
        ObjectHelper.notEmpty(config.getProtocol(), "protocol");

        // Use global ssl if present
        if (endpoint.getConfiguration().getSslContextParameters() == null) {
            endpoint.getConfiguration().setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    private void configureAdditionalJavaMailProperties(MailConfiguration config, Map<String, Object> parameters) {
        // we cannot remove while iterating, as we will get a modification exception
        Set<Object> toRemove = new HashSet<Object>();

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey().toString().startsWith("mail.")) {
                config.getAdditionalJavaMailProperties().put(entry.getKey(), entry.getValue());
                toRemove.add(entry.getKey());
            }
        }

        for (Object key : toRemove) {
            parameters.remove(key);
        }
    }

    public MailConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new MailConfiguration(getCamelContext());
        }
        return configuration;
    }

    /**
     * Sets the Mail configuration
     *
     * @param configuration the configuration to use by default for endpoints
     */
    public void setConfiguration(MailConfiguration configuration) {
        this.configuration = configuration;
    }

    public ContentTypeResolver getContentTypeResolver() {
        return contentTypeResolver;
    }

    /**
     * Resolver to determine Content-Type for file attachments.
     */
    public void setContentTypeResolver(ContentTypeResolver contentTypeResolver) {
        this.contentTypeResolver = contentTypeResolver;
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
