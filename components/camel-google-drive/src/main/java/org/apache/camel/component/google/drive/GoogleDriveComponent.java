package org.apache.camel.component.google.drive;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.util.component.AbstractApiComponent;

import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;

/**
 * Represents the component that manages {@link GoogleDriveEndpoint}.
 */
@UriEndpoint(scheme = "google-drive", consumerClass = GoogleDriveConsumer.class, consumerPrefix = "consumer")
public class GoogleDriveComponent extends AbstractApiComponent<GoogleDriveApiName, GoogleDriveConfiguration, GoogleDriveApiCollection> {

    public GoogleDriveComponent() {
        super(GoogleDriveEndpoint.class, GoogleDriveApiName.class, GoogleDriveApiCollection.getCollection());
    }

    public GoogleDriveComponent(CamelContext context) {
        super(context, GoogleDriveEndpoint.class, GoogleDriveApiName.class, GoogleDriveApiCollection.getCollection());
    }

    @Override
    protected GoogleDriveApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return GoogleDriveApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, GoogleDriveApiName apiName,
                                      GoogleDriveConfiguration endpointConfiguration) {
        return new GoogleDriveEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }
}
