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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.HeaderFilterStrategyAware;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;

/**
 * Component for JavaMail.
 *
 * @version $Revision:520964 $
 */
public class MailComponent extends DefaultComponent implements HeaderFilterStrategyAware {
    private MailConfiguration configuration;
    private HeaderFilterStrategy headerFilterStrategy = new DefaultHeaderFilterStrategy();

    public MailComponent() {
        this.configuration = new MailConfiguration();
    }

    public MailComponent(MailConfiguration configuration) {
        this.configuration = configuration;
    }

    public MailComponent(CamelContext context) {
        super(context);
        this.configuration = new MailConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        URI url = new URI(uri);
        if ("nntp".equalsIgnoreCase(url.getScheme())) {
            throw new UnsupportedOperationException("nntp protocol is not supported");
        }

        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");
        MailConfiguration config = configuration.copy();

        // only configure if we have a url with a known protocol
        config.configure(url);
        configureAdditionalJavaMailProperties(config, parameters);

        MailEndpoint endpoint = new MailEndpoint(uri, this, config);
        setProperties(endpoint.getConfiguration(), parameters);

        // sanity check that we know the mail server
        ObjectHelper.notEmpty(config.getHost(), "host");
        ObjectHelper.notEmpty(config.getProtocol(), "protocol");
        if (config.getPort() <= 0) {
            throw new IllegalArgumentException("port mut be specified");
        }

        return endpoint;
    }

    private void configureAdditionalJavaMailProperties(MailConfiguration config, Map parameters) {
        // we cannot remove while iterating, as we will get a modification exception
        Set toRemove = new HashSet();

        for (Object key : parameters.keySet()) {
            if (key.toString().startsWith("mail.")) {
                config.getAdditionalJavaMailProperties().put(key, parameters.get(key));
                toRemove.add(key);
            }
        }

        for (Object key : toRemove) {
            parameters.remove(key);
        }
    }

    public MailConfiguration getConfiguration() {
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

    /**
     * A strategy method allowing the URI destination to be translated into the actual Mail destination name
     * (say by looking up in JNDI or something)
     */
    protected String convertPathToActualDestination(String path) {
        return path;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }
}
