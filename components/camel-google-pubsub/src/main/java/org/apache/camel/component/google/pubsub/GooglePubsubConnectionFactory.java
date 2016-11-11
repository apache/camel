package org.apache.camel.component.google.pubsub;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Strings;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.PubsubScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.Collections;

public class GooglePubsubConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(GooglePubsubConnectionFactory.class);

    private static JsonFactory JSON_FACTORY;
    private static HttpTransport TRANSPORT;

    private String serviceAccount = null;
    private String serviceAccountKey = null;
    private String credentialsFileLocation = null;
    private String serviceURL = null;

    private Pubsub client=null;

    public GooglePubsubConnectionFactory() {
        JSON_FACTORY = new JacksonFactory();

        try {
            TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized Pubsub getClient() throws Exception {
        if (this.client == null)
            this.client = buildClient();

       return this.client;
    }

    private Pubsub buildClient() throws Exception {

        GoogleCredential credential = null;

        if (!Strings.isNullOrEmpty(serviceAccount) && !Strings.isNullOrEmpty(serviceAccountKey)){
            if (log.isDebugEnabled())
                log.debug("Service Account and Key have been set explicitly. Initialising PubSub using Service Account " + serviceAccount);
            credential = createFromAccountKeyPair();
        }

        if (credential == null && !Strings.isNullOrEmpty(credentialsFileLocation)){
            if (log.isDebugEnabled())
                log.debug("Key File Name has been set explicitly. Initialising PubSub using Key File " + credentialsFileLocation);
            credential = createFromFile();
        }
        else{
            if (log.isDebugEnabled())
                log.debug("No explicit Service Account or Key File Name have been provided. Initialising PubSub using defaults ");
            credential = createDefault();
        }

        Pubsub.Builder builder = new Pubsub.Builder(TRANSPORT, JSON_FACTORY, credential)
                                                  .setApplicationName("camel-google-pubsub");

        // Local emulator, SOCKS proxy, etc.
        if (serviceURL != null) builder.setRootUrl(serviceURL);

        return builder.build();
    }

    private GoogleCredential createFromFile() throws Exception {

        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(credentialsFileLocation));

        if (credential.createScopedRequired()) {
            credential = credential.createScoped(PubsubScopes.all());
        }

        return credential;
    }

    private GoogleCredential createDefault() throws Exception {
        GoogleCredential credential = GoogleCredential.getApplicationDefault();

        Collection pubSubScopes = Collections.singletonList(PubsubScopes.PUBSUB);

        if (credential.createScopedRequired()) {
            credential = credential.createScoped(pubSubScopes);
        }

        return credential;
    }

    private GoogleCredential createFromAccountKeyPair() {
        try {

            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(TRANSPORT)
                    .setJsonFactory(JSON_FACTORY)
                    .setServiceAccountId(serviceAccount)
                    .setServiceAccountScopes(PubsubScopes.all())
                    .setServiceAccountPrivateKey(getPrivateKeyFromString(serviceAccountKey))
                    .build();

            return credential;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PrivateKey getPrivateKeyFromString(String serviceKeyPem) {
        PrivateKey privateKey = null;
        try {

            String privKeyPEM = serviceKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                                             .replace("-----END PRIVATE KEY-----", "")
                                             .replace("\r", "")
                                             .replace("\n", "");

            byte[] encoded = Base64.decodeBase64(privKeyPEM);

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            privateKey = KeyFactory.getInstance("RSA")
                                   .generatePrivate(keySpec);
        } catch (Exception e) {
            String _error = "Constructing Private Key from PEM string failed: " + e.getMessage();
            log.error(_error, e);
            throw new RuntimeException(e);
        }
        return privateKey;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    public GooglePubsubConnectionFactory setServiceAccount(String serviceAccount) {
        this.serviceAccount = serviceAccount;
        resetClient();
        return this;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public GooglePubsubConnectionFactory setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
        resetClient();
        return this;
    }

    public String getCredentialsFileLocation() {
        return credentialsFileLocation;
    }

    public GooglePubsubConnectionFactory setCredentialsFileLocation(String credentialsFileLocation) {
        this.credentialsFileLocation = credentialsFileLocation;
        resetClient();
        return this;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public GooglePubsubConnectionFactory setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
        resetClient();
        return this;
    }

    private synchronized void resetClient(){
        this.client = null;
    }
}
