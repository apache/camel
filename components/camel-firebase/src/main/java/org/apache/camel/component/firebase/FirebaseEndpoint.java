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
package org.apache.camel.component.firebase;

import com.google.firebase.FirebaseApp;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Represents a Firebase endpoint.
 */
@UriEndpoint(scheme = "firebase", title = "Firebase", syntax = "firebase:name", consumerClass = FirebaseConsumer.class, label = "Firebase")
public class FirebaseEndpoint extends DefaultEndpoint {

    private final FirebaseConfig firebaseConfig;

    @UriPath(description = "The Firebase database URL. Always uses https")
    @Metadata(required = "true")
    private String databaseUrl;

    @UriParam(description = "The path in the database tree where the key value pairs are to be stored")
    @Metadata(required = "true")
    private String rootReference;

    @UriParam(description = "The path to the JSON file which provided the keys used to connect to Firebase. #"
            + "This file is typically generated when you create the database")
    @Metadata(required = "true")
    private String serviceAccountFile;

    @UriParam(defaultValue = "firebaseKey", description = "The Camel exchange header name in which "
            + "the Firebase key is specified. Only needed in case you are saving or updating data")
    @Metadata(required = "false")
    private String keyName = "firebaseKey";

    @UriParam(defaultValue = "async", description = "If true, the save or update request (set value in Firebase terms) "
            + "is fired and the reply will be ignored, else the routing thread will wait and the reply will be saved in the exchange message")
    @Metadata(required = "false")
    private boolean async;

    public FirebaseEndpoint(String uri, FirebaseComponent firebaseComponent, FirebaseConfig firebaseConfig) {
        super(uri, firebaseComponent);
        this.firebaseConfig = firebaseConfig;
        this.setRootReference(firebaseConfig.getRootReference());
        this.setServiceAccountFile(firebaseConfig.getServiceAccountFile());
        this.databaseUrl = firebaseConfig.getDatabaseUrl();
        final String keyName = firebaseConfig.getKeyName();
        this.setAsync(firebaseConfig.isAsync());
        if (keyName != null) {
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

    public String getDatabaseUrl() {
        return databaseUrl;
    }
}
