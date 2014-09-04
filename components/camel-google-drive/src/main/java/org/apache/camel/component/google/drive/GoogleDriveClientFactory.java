package org.apache.camel.component.google.drive;

import java.util.Collection;

import com.google.api.services.drive.Drive;

public interface GoogleDriveClientFactory {

    public abstract Drive makeClient(String clientId, String clientSecret, Collection<String> scopes);

}