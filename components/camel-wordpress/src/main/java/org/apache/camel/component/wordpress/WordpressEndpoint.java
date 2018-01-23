package org.apache.camel.component.wordpress;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.wordpress.config.WordpressEndpointConfiguration;
import org.apache.camel.component.wordpress.consumer.WordpressPostConsumer;
import org.apache.camel.component.wordpress.consumer.WordpressUserConsumer;
import org.apache.camel.component.wordpress.producer.WordpressPostProducer;
import org.apache.camel.component.wordpress.producer.WordpressUserProducer;
import org.apache.camel.component.wordpress.proxy.WordpressOperationType;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.wordpress4j.WordpressAPIConfiguration;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.auth.WordpressBasicAuthentication;
import org.wordpress4j.model.SearchCriteria;

/**
 * Represents a Wordpress endpoint.
 */
@UriEndpoint(firstVersion = "2.20.1", scheme = "wordpress", title = "Wordpress", syntax = "wordpress:operation", label = "Wordpress")
public class WordpressEndpoint extends DefaultEndpoint {

    public static final String ENDPOINT_SERVICE_POST = "post, user";

    @UriPath(description = "The endpoint operation.", enums = ENDPOINT_SERVICE_POST)
    @Metadata(required = "true")
    private String operation;

    @UriPath(description = "The second part of an endpoint operation. Needed only when endpoint semantic is not enough, like wordpress:post:delete", enums = "delete")
    private String operationDetail;

    @UriParam
    private WordpressEndpointConfiguration config;

    public WordpressEndpoint(String uri, WordpressComponent component, WordpressEndpointConfiguration configuration) {
        super(uri, component);
        this.config = configuration;
    }

    public WordpressEndpointConfiguration getConfig() {
        return config;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperationDetail() {
        return operationDetail;
    }

    public void setOperationDetail(String operationDetail) {
        this.operationDetail = operationDetail;
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        switch (WordpressOperationType.valueOf(operation)) {
        case post:
            return new WordpressPostProducer(this);
        case user:
            return new WordpressUserProducer(this);
        default:
            break;
        }
        throw new UnsupportedOperationException(String.format("Operation '%s' not supported.", operation));
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        switch (WordpressOperationType.valueOf(operation)) {
        case post:
            return new WordpressPostConsumer(this, processor);
        case user:
            return new WordpressUserConsumer(this, processor);
        default:
            break;
        }
        throw new UnsupportedOperationException(String.format("Operation '%s' not supported.", operation));
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);

        // set configuration properties first
        try {
            if (config == null) {
                config = new WordpressEndpointConfiguration();
            }
            EndpointHelper.setReferenceProperties(getCamelContext(), config, options);
            EndpointHelper.setProperties(getCamelContext(), config, options);

            if (config.getSearchCriteria() == null) {
                final SearchCriteria searchCriteria = WordpressOperationType.valueOf(operation).getCriteriaType()
                        .newInstance();
                Map<String, Object> criteriaOptions = IntrospectionSupport.extractProperties(options, "criteria.");
                // any property that has a "," should be a List
                criteriaOptions = criteriaOptions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    if (e != null && e.toString().indexOf(",") > -1) {
                        return Arrays.asList(e.toString().split(","));
                    }
                    return e.getValue();
                }));
                IntrospectionSupport.setProperties(searchCriteria, criteriaOptions);
                config.setSearchCriteria(searchCriteria);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        // validate configuration
        config.validate();
        this.initServiceProvider();
    }

    private void initServiceProvider() {
        final WordpressAPIConfiguration apiConfiguration = new WordpressAPIConfiguration(config.getUrl(),
                config.getApiVersion());
        // basic auth
        if (ObjectHelper.isNotEmpty(config.getUser())) {
            apiConfiguration.setAuthentication(
                    new WordpressBasicAuthentication(config.getUser(), config.getPassword()));
        }

        WordpressServiceProvider.getInstance().init(apiConfiguration);
    }

}
