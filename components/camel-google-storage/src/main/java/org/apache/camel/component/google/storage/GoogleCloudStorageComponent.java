package org.apache.camel.component.google.storage;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("google-storage")
public class GoogleCloudStorageComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageComponent.class);

    @Metadata
    private GoogleCloudStorageComponentConfiguration configuration = new GoogleCloudStorageComponentConfiguration();

    public GoogleCloudStorageComponent() {
        this(null);
    }

    public GoogleCloudStorageComponent(CamelContext context) {
        super(context);

        //registerExtension(new GoogleCloudStorageComponentVerifierExtension());
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LOG.debug("create endopoint: uri={}, remaining={}, parameters={}", uri, remaining, parameters);

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Bucket name must be specified.");
        }
        if (remaining.startsWith("arn:")) {
            remaining = remaining.substring(remaining.lastIndexOf(':') + 1, remaining.length());
        }
        final GoogleCloudStorageComponentConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new GoogleCloudStorageComponentConfiguration();
        setProperties(configuration, parameters);
        configuration.setBucketName(remaining);
        Endpoint endpoint = new GoogleCloudStorageEndpoint(uri, this, configuration);

        return endpoint;
    }

    public GoogleCloudStorageComponentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(GoogleCloudStorageComponentConfiguration configuration) {
        this.configuration = configuration;
    }

}
