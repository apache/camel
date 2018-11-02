package org.apache.camel.component.nsq;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;

import java.util.Map;

/**
 * Represents the component that manages {@link NsqEndpoint}.
 */
public class NsqComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NsqConfiguration config = new NsqConfiguration();
        setProperties(config, parameters);
        config.setServers(remaining);

        NsqEndpoint endpoint = new NsqEndpoint(uri, this, config);
        return endpoint;
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
