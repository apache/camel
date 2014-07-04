package org.apache.camel.component.google.drive;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.component.google.drive.internal.GoogleDriveConstants;
import org.apache.camel.component.google.drive.internal.GoogleDrivePropertiesHelper;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

/**
 * Represents a GoogleDrive endpoint.
 */
@UriEndpoint(scheme = "google-drive", consumerClass = GoogleDriveConsumer.class, consumerPrefix = "consumer")
public class GoogleDriveEndpoint extends AbstractApiEndpoint<GoogleDriveApiName, GoogleDriveConfiguration> {
    private Object apiProxy;
    private Drive client;
    
    // TODO these need to be configurable
    private NetHttpTransport transport = new NetHttpTransport();
    private JacksonFactory jsonFactory = new JacksonFactory();
    private FileDataStoreFactory dataStoreFactory;
    
    // Directory to store user credentials
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
        System.getProperty("user.home"), ".store/drive_sample");
    

    
    public GoogleDriveEndpoint(String uri, GoogleDriveComponent component,
                         GoogleDriveApiName apiName, String methodName, GoogleDriveConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, GoogleDriveApiCollection.getCollection().getHelper(apiName), endpointConfiguration);

    }

    public Producer createProducer() throws Exception {
        return new GoogleDriveProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final GoogleDriveConsumer consumer = new GoogleDriveConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<GoogleDriveConfiguration> getPropertiesHelper() {
        return GoogleDrivePropertiesHelper.getHelper();
    }

    protected String getThreadProfileName() {
        return GoogleDriveConstants.THREAD_PROFILE_NAME;
    }

    // Authorizes the installed application to access user's protected data.
    private Credential authorize() throws Exception {
      dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
      // set up authorization code flow
      // TODO refresh token support too
      GoogleAuthorizationCodeFlow flow =
          new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, configuration.getClientId(), configuration.getClientSecret(),
              Collections.singleton(DriveScopes.DRIVE_FILE)).setDataStoreFactory(dataStoreFactory)
              .build();
      // authorize
      return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    
    @Override
    protected void afterConfigureProperties() {
        // TODO create API proxy, set connection properties, etc.
        
        switch ((GoogleDriveApiName) apiName) {
            case DRIVE_FILES:
                apiProxy = getClient().files();
                break;
                // TODO add extra APIs here
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        } 
    }

    public Drive getClient() {
        if (client == null) {
            Credential credential;
            try {
                credential = authorize();
                client = new Drive.Builder(transport, jsonFactory, credential).build();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return client;
    }
    
    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }
}
