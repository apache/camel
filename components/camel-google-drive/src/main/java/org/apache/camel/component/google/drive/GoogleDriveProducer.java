package org.apache.camel.component.google.drive;

import java.io.IOException;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.component.AbstractApiProducer;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.component.google.drive.internal.GoogleDriveConstants;
import org.apache.camel.component.google.drive.internal.GoogleDrivePropertiesHelper;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;

/**
 * The GoogleDrive producer.
 */
public class GoogleDriveProducer extends AbstractApiProducer<GoogleDriveApiName, GoogleDriveConfiguration> {

    public GoogleDriveProducer(GoogleDriveEndpoint endpoint) {
        super(endpoint, GoogleDrivePropertiesHelper.getHelper());
    }

    @Override
    protected Object doInvokeMethod(ApiMethod method, Map<String, Object> properties) throws RuntimeCamelException {
        AbstractGoogleClientRequest request = (AbstractGoogleClientRequest) super.doInvokeMethod(method, properties);
        // TODO set any generic params, like OAuth token, etc.
        try {
            return request.execute();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    protected String getThreadProfileName() {
        return GoogleDriveConstants.THREAD_PROFILE_NAME;
    }
}
