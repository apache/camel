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
package org.apache.camel.component.mail;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.mail.search.SearchTerm;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;
import org.eclipse.angus.mail.imap.SortTerm;

/**
 * Component for JavaMail.
 */
@Component("imap,imaps,pop3,pop3s,smtp,smtps")
public class MailComponent extends HealthCheckComponent implements HeaderFilterStrategyAware, SSLContextParametersAware {

    @Metadata(label = "advanced")
    private MailConfiguration configuration;
    @Metadata(label = "advanced")
    private ContentTypeResolver contentTypeResolver;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "filter",
              description = "To use a custom org.apache.camel.spi.HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;

    public MailComponent() {
    }

    public MailComponent(MailConfiguration configuration) {
        this.configuration = configuration;
    }

    public MailComponent(CamelContext context) {
        super(context);
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
                st = MailConverters.toSearchTerm((SimpleSearchTerm) searchTerm);
            } else {
                st = getCamelContext().getTypeConverter().mandatoryConvertTo(SearchTerm.class, searchTerm);
            }
            endpoint.setSearchTerm(st);
        }

        // special for sort term
        Object sortTerm = getAndRemoveOrResolveReferenceParameter(parameters, "sortTerm", Object.class);
        if (sortTerm != null) {
            SortTerm[] st;
            if (sortTerm instanceof String) {
                // okay its a String then lets convert that to SortTerm
                st = MailConverters.toSortTerm((String) sortTerm);
            } else if (sortTerm instanceof SortTerm[]) {
                st = (SortTerm[]) sortTerm;
            } else {
                throw new IllegalArgumentException("SortTerm must either be SortTerm[] or a String value");
            }
            endpoint.setSortTerm(st);
        }

        // special for searchTerm.xxx options
        Map<String, Object> sstParams = PropertiesHelper.extractProperties(parameters, "searchTerm.");
        if (!sstParams.isEmpty()) {
            // use SimpleSearchTerm as POJO to store the configuration and then convert that to the actual SearchTerm
            SimpleSearchTerm sst = new SimpleSearchTerm();
            setProperties(sst, sstParams);
            SearchTerm st = MailConverters.toSearchTerm(sst);
            endpoint.setSearchTerm(st);
        }

        endpoint.setContentTypeResolver(contentTypeResolver);
        setEndpointHeaderFilterStrategy(endpoint);
        setProperties(endpoint, parameters);

        // sanity check that we know the mail server
        StringHelper.notEmpty(config.getHost(), "host");
        StringHelper.notEmpty(config.getProtocol(), "protocol");

        // Use global ssl if present
        if (endpoint.getConfiguration().getSslContextParameters() == null) {
            endpoint.getConfiguration().setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    private void configureAdditionalJavaMailProperties(MailConfiguration config, Map<String, Object> parameters) {
        // we cannot remove while iterating, as we will get a modification exception
        Set<Object> toRemove = new HashSet<>();

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

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }

    /**
     * Sets the header filter strategy to use from the given endpoint if the endpoint is a
     * {@link HeaderFilterStrategyAware} type.
     */
    public void setEndpointHeaderFilterStrategy(Endpoint endpoint) {
        if (headerFilterStrategy != null && endpoint instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) endpoint).setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

}
