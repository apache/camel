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
package org.apache.camel.component.facebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.component.facebook.config.FacebookNameStyle;
import org.apache.camel.component.facebook.data.FacebookMethodsType;
import org.apache.camel.component.facebook.data.FacebookPropertiesHelper;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.convertToGetMethod;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.convertToSearchMethod;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.getCandidateMethods;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.getMissingProperties;
import static org.apache.camel.component.facebook.data.FacebookPropertiesHelper.getEndpointPropertyNames;

/**
 * Represents a Facebook endpoint.
 */
@UriEndpoint(scheme = "facebook", title = "Facebook", syntax = "facebook:methodName", consumerClass = FacebookConsumer.class, label = "social")
public class FacebookEndpoint extends DefaultEndpoint implements FacebookConstants {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookEndpoint.class);

    // Facebook4J method name
    @UriPath @Metadata(required = "true")
    private final String methodName;
    private FacebookNameStyle nameStyle;

    @UriParam
    private FacebookEndpointConfiguration configuration;

    // property name for Exchange 'In' message body
    @UriParam
    private String inBody;

    // candidate methods based on method name and endpoint configuration
    private List<FacebookMethodsType> candidates;

    public FacebookEndpoint(String uri, FacebookComponent facebookComponent,
                            String remaining, FacebookEndpointConfiguration configuration) {
        super(uri, facebookComponent);
        this.configuration = configuration;
        this.methodName = remaining;
    }

    public Producer createProducer() throws Exception {
        return new FacebookProducer(this);
    }

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

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);

        // set configuration properties first
        try {
            if (configuration == null) {
                configuration = new FacebookEndpointConfiguration();
            }
            EndpointHelper.setReferenceProperties(getCamelContext(), configuration, options);
            EndpointHelper.setProperties(getCamelContext(), configuration, options);
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
        final Set<String> arguments = new HashSet<String>();
        arguments.addAll(getEndpointPropertyNames(configuration));
        // add inBody argument for producers
        if (inBody != null) {
            arguments.add(inBody);
        }
        final String[] argNames = arguments.toArray(new String[arguments.size()]);

        candidates = new ArrayList<FacebookMethodsType>();
        candidates.addAll(getCandidateMethods(methodName, argNames));
        if (!candidates.isEmpty()) {
            // found an exact name match, allows disambiguation if needed
            this.nameStyle = FacebookNameStyle.EXACT;
        } else {

            // also search for long forms of method name, both get* and search*
            // Note that this set will be further sorted by Producers and Consumers
            // producers will prefer get* forms, and consumers should prefer search* forms
            candidates.addAll(getCandidateMethods(convertToGetMethod(methodName), argNames));
            if (!candidates.isEmpty()) {
                this.nameStyle = FacebookNameStyle.GET;
            }

            int nGetMethods = candidates.size();
            candidates.addAll(getCandidateMethods(convertToSearchMethod(methodName), argNames));
            // error if there are no candidates
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("No matching operation for %s, with arguments %s", methodName, arguments));
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
            final Set<String> missing = getMissingProperties(methodName, nameStyle, arguments);
            if (!missing.isEmpty()) {
                LOG.debug("Method {} could use one or more properties from {}", methodName, missing);
            }
        }
    }

    public FacebookEndpointConfiguration getConfiguration() {
        return configuration;
    }

    public String getMethodName() {
        return methodName;
    }

    public FacebookNameStyle getNameStyle() {
        return nameStyle;
    }

    public List<FacebookMethodsType> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public String getInBody() {
        return inBody;
    }

    public void setInBody(String inBody) {
        // validate property name
        ObjectHelper.notNull(inBody, "inBody");
        if (!FacebookPropertiesHelper.getValidEndpointProperties().contains(inBody)) {
            throw new IllegalArgumentException("Unknown property " + inBody);
        }
        this.inBody = inBody;
    }

}
