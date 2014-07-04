package org.apache.camel.component.google.drive.internal;

import org.apache.camel.util.component.ApiMethodPropertiesHelper;

import org.apache.camel.component.google.drive.GoogleDriveConfiguration;

/**
 * Singleton {@link ApiMethodPropertiesHelper} for GoogleDrive component.
 */
public final class GoogleDrivePropertiesHelper extends ApiMethodPropertiesHelper<GoogleDriveConfiguration> {

    private static GoogleDrivePropertiesHelper helper;

    private GoogleDrivePropertiesHelper() {
        super(GoogleDriveConfiguration.class, GoogleDriveConstants.PROPERTY_PREFIX);
    }

    public static synchronized GoogleDrivePropertiesHelper getHelper() {
        if (helper == null) {
            helper = new GoogleDrivePropertiesHelper();
        }
        return helper;
    }
}
