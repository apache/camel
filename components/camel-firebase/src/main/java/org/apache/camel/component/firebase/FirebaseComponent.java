package org.apache.camel.component.firebase;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

import java.util.Map;

/**
 * Represents the component that manages {@link FirebaseEndpoint}.
 */
public class FirebaseComponent extends UriEndpointComponent {

    public FirebaseComponent() {
        super(FirebaseEndpoint.class);
    }

    public FirebaseComponent(CamelContext context) {
        super(context, FirebaseEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        FirebaseConfig firebaseConfig = new FirebaseConfig.Builder(
                String.format("https://%s", remaining),
                (String) parameters.get("rootReference"),
                (String) parameters.get("serviceAccountFile"))
                .build();

        firebaseConfig.init();

        Endpoint endpoint = new FirebaseEndpoint(uri, this, firebaseConfig);
        setProperties(endpoint, parameters);
        return endpoint;
    }

}
