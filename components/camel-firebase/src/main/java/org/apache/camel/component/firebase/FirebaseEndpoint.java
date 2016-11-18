package org.apache.camel.component.firebase;

import com.google.firebase.FirebaseApp;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents a Firebase endpoint.
 */
@UriEndpoint(scheme = "firebase", title = "Firebase", syntax="firebase:name", consumerClass = FirebaseConsumer.class, label = "Firebase")
public class FirebaseEndpoint extends DefaultEndpoint {

    private final FirebaseConfig firebaseConfig;

    @UriParam
    @Metadata(required = "true")
    private String rootReference;

    @UriParam
    @Metadata(required = "true")
    private String serviceAccountFile;

    @UriParam(defaultValue = "firebaseKey") @Metadata(required = "false")
    private String keyName = "firebaseKey";

    @UriParam(defaultValue = "async") @Metadata(required = "false")
    private boolean async;

    public FirebaseEndpoint(String uri, FirebaseComponent firebaseComponent, FirebaseConfig firebaseConfig) {
        super(uri, firebaseComponent);
        this.firebaseConfig = firebaseConfig;
        this.setRootReference(firebaseConfig.getRootReference());
        this.setServiceAccountFile(firebaseConfig.getServiceAccountFile());
        final String keyName = firebaseConfig.getKeyName();
        this.setAsync(firebaseConfig.isAsync());
        if(keyName != null) {
            this.setKeyName(keyName);
        }
    }

    public Producer createProducer() throws Exception {
        return new FirebaseProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new FirebaseConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public String getRootReference() {
        return rootReference;
    }

    public void setRootReference(String rootReference) {
        this.rootReference = rootReference;
    }

    public String getServiceAccountFile() {
        return serviceAccountFile;
    }

    public void setServiceAccountFile(String serviceAccountFile) {
        this.serviceAccountFile = serviceAccountFile;
    }

    public FirebaseConfig getFirebaseConfig() {
        return firebaseConfig;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public FirebaseApp getFirebaseApp() {
        return firebaseConfig.getFirebaseApp();
    }
}
