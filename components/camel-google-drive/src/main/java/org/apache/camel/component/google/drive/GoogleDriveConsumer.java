package org.apache.camel.component.google.drive;

import java.io.IOException;
import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.component.AbstractApiConsumer;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;

/**
 * The GoogleDrive consumer.
 */
public class GoogleDriveConsumer extends AbstractApiConsumer<GoogleDriveApiName, GoogleDriveConfiguration> {

    public GoogleDriveConsumer(GoogleDriveEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    } 
    
    @Override
    protected Object doInvokeMethod(Map<String, Object> properties) throws RuntimeCamelException {
        AbstractGoogleClientRequest request = (AbstractGoogleClientRequest) super.doInvokeMethod(properties);
        // TODO set any generic params, like OAuth token, etc.
        try {
            return request.execute();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }    
}
