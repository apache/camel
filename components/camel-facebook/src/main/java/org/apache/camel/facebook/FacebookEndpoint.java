package org.apache.camel.facebook;

import java.util.*;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.facebook.config.FacebookNameStyle;
import org.apache.camel.facebook.data.FacebookMethodsType;
import org.apache.camel.facebook.data.FacebookPropertiesHelper;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.EndpointHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.facebook.data.FacebookMethodsTypeHelper.convertToGetMethod;
import static org.apache.camel.facebook.data.FacebookMethodsTypeHelper.convertToSearchMethod;
import static org.apache.camel.facebook.data.FacebookMethodsTypeHelper.getCandidateMethods;
import static org.apache.camel.facebook.data.FacebookMethodsTypeHelper.getMissingProperties;
import static org.apache.camel.facebook.data.FacebookPropertiesHelper.getEndpointPropertyNames;

/**
 * Represents a Facebook endpoint.
 */
@UriEndpoint(scheme = "facebook", consumerClass = FacebookConsumer.class)
public class FacebookEndpoint extends DefaultEndpoint implements FacebookConstants {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookEndpoint.class);

    @UriParam
    private FacebookEndpointConfiguration configuration;

    // Facebook4J method name
    private final String methodName;
    private FacebookNameStyle nameStyle;

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
        final Set<String> arguments = getEndpointPropertyNames(configuration);
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

            candidates.addAll(getCandidateMethods(convertToSearchMethod(methodName), argNames));
            // error if there are no candidates
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("No matching operation for %s, with arguments %s", methodName, arguments));
            }

            if (nameStyle == null) {
                // no get* methods found
                nameStyle = FacebookNameStyle.SEARCH;
            } else {
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

}
