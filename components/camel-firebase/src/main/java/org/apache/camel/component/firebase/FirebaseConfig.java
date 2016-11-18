package org.apache.camel.component.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Contains the elements needed to connect to Firebase
 */
public class FirebaseConfig {

    private final String databaseUrl;

    private final String rootReference;

    private final String serviceAccountFile;

    private final String keyName;

    private final boolean async;

    private FirebaseApp firebaseApp;

    private FirebaseConfig(Builder builder) {
        this.databaseUrl = builder.databaseUrl;
        this.rootReference = builder.rootReference;
        this.serviceAccountFile = builder.serviceAccountFile;
        this.keyName = builder.keyName;
        this.async = builder.async;
    }

    public void init() throws IOException {
        try(InputStream in = Files.newInputStream(Paths.get(serviceAccountFile))) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setServiceAccount(in)
                    .setDatabaseUrl(this.databaseUrl)
                    .build();
            firebaseApp = FirebaseApp.initializeApp(options, UUID.randomUUID().toString());
        }
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public String getRootReference() {
        return rootReference;
    }

    public String getServiceAccountFile() {
        return serviceAccountFile;
    }

    public String getKeyName() {
        return keyName;
    }

    public FirebaseApp getFirebaseApp() {
        return firebaseApp;
    }

    public boolean isAsync() {
        return async;
    }

    public static class Builder {

        private final String databaseUrl;

        private final String rootReference;

        private final String serviceAccountFile;

        private String keyName;

        private boolean async;

        public Builder(String databaseUrl, String rootReference, String serviceAccountFile) {
            this.databaseUrl = databaseUrl;
            this.rootReference = rootReference;
            this.serviceAccountFile = serviceAccountFile;
        }

        public Builder setKeyName(String keyName) {
            this.keyName = keyName;
            return this;
        }

        public Builder setAsync(boolean async) {
            this.async = async;
            return this;
        }

        public FirebaseConfig build() {
            return new FirebaseConfig(this);
        }
    }
}
