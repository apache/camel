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
package org.apache.camel.component.facebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Consumer;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.component.facebook.config.FacebookNameStyle;
import org.apache.camel.component.facebook.data.FacebookMethodsType;
import org.apache.camel.component.facebook.data.FacebookPropertiesHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.convertToGetMethod;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.convertToSearchMethod;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.getCandidateMethods;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.getMissingProperties;
import static org.apache.camel.component.facebook.data.FacebookPropertiesHelper.getEndpointPropertyNames;

/**
 * The Facebook component provides access to all of the Facebook APIs accessible using Facebook4J.
 *
 * It allows producing messages to retrieve, add, and delete posts, likes, comments, photos, albums, videos, photos,
 * checkins, locations, links, etc. It also supports APIs that allow polling for posts, users, checkins, groups, locations, etc.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "facebook", title = "Facebook", syntax = "facebook:methodName", label = "social")
public class FacebookEndpoint extends DefaultEndpoint implements FacebookConstants {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookEndpoint.class);

    private FacebookNameStyle nameStyle;

    @UriPath(name = "methodName", description = "What operation to perform") @Metadata(required = true)

    private String method;
    private FacebookMethodsType methodName;
    @UriParam
    private FacebookEndpointConfiguration configuration;
    @UriParam
    private String inBody;

    // candidate methods based on method name and endpoint configuration
    private List<FacebookMethodsType> candidates;

    public FacebookEndpoint(String uri, FacebookComponent facebookComponent,
                            String remaining, FacebookEndpointConfiguration configuration) throws NoTypeConversionAvailableException {
        super(uri, facebookComponent);
        this.configuration = configuration;
        this.method = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FacebookProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final FacebookConsumer consumer = new FacebookConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);

        // set configuration properties first
        try {
            if (configuration == null) {
                configuration = new FacebookEndpointConfiguration();
            }
            PropertyBindingSupport.bindProperties(getCamelContext(), configuration, options);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        // extract reading properties
        FacebookPropertiesHelper.configureReadingProperties(configuration, options);

        // validate configuration
        configuration.validate();
        // validate and initialize state
        initState();
    }

    private void initState() {
        // get endpoint property names
        final Set<String> arguments = new HashSet<>();
        arguments.addAll(getEndpointPropertyNames(getCamelContext(), configuration));
        // add inBody argument for producers
        if (inBody != null) {
            arguments.add(inBody);
        }
        final String[] argNames = arguments.toArray(new String[arguments.size()]);

        candidates = new ArrayList<>();
        candidates.addAll(getCandidateMethods(method, argNames));
        if (!candidates.isEmpty()) {
            // found an exact name match, allows disambiguation if needed
            this.nameStyle = FacebookNameStyle.EXACT;
        } else {

            // also search for long forms of method name, both get* and search*
            // Note that this set will be further sorted by Producers and Consumers
            // producers will prefer get* forms, and consumers should prefer search* forms
            candidates.addAll(getCandidateMethods(convertToGetMethod(method), argNames));
            if (!candidates.isEmpty()) {
                this.nameStyle = FacebookNameStyle.GET;
            }

            int nGetMethods = candidates.size();
            candidates.addAll(getCandidateMethods(convertToSearchMethod(method), argNames));
            // error if there are no candidates
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("No matching operation for %s, with arguments %s", method, arguments));
            }

            if (nameStyle == null) {
                // no get* methods found
                nameStyle = FacebookNameStyle.SEARCH;
            } else if (candidates.size() > nGetMethods) {
                // get* and search* methods found
                nameStyle = FacebookNameStyle.GET_AND_SEARCH;
            }
        }

        // log missing/extra properties for debugging
        if (LOG.isDebugEnabled()) {
            final Set<String> missing = getMissingProperties(method, nameStyle, arguments);
            if (!missing.isEmpty()) {
                LOG.debug("Method {} could use one or more properties from {}", method, missing);
            }
        }
    }

    public FacebookEndpointConfiguration getConfiguration() {
        return configuration;
    }

    public List<FacebookMethodsType> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public String getInBody() {
        return inBody;
    }

    public String getMethod() {
        return method;
    }

    public FacebookNameStyle getNameStyle() {
        return nameStyle;
    }

    /**
     * Sets the name of a parameter to be passed in the exchange In Body
     */
    public void setInBody(String inBody) {
        // validate property name
        ObjectHelper.notNull(inBody, "inBody");
        if (!FacebookPropertiesHelper.getValidEndpointProperties().contains(inBody)) {
            throw new IllegalArgumentException("Unknown property " + inBody);
        }
        this.inBody = inBody;
    }

    /**
     * Sets the {@link FacebookEndpointConfiguration} to use
     *
     * @param configuration the {@link FacebookEndpointConfiguration} to use
     */
    public void setConfiguration(FacebookEndpointConfiguration configuration) {
        this.configuration = configuration;
    }

}
