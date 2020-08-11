package org.apache.camel.component.azure.eventhubs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class TestUtils {

    public static final String CONNECTION_STRING = "connectionString";
    public static final String BLOB_ACCOUNT_NAME = "blobAccountName";
    public static final String BLOB_ACCESS_KEY = "blobAccessKey";

    private TestUtils() {
    }

    public static Properties loadAzurePropertiesFile() throws IOException {
        final Properties properties = new Properties();
        final String fileName = "azure_key.properties";

        final InputStream inputStream = Objects.requireNonNull(TestUtils.class.getClassLoader().getResourceAsStream(fileName));

        properties.load(inputStream);

        return properties;
    }

    public static Properties loadAzureAccessFromJvmEnv() throws Exception {
        final Properties properties = new Properties();
        if (System.getProperty(CONNECTION_STRING) == null || System.getProperty(BLOB_ACCOUNT_NAME) == null ||
                System.getProperty(BLOB_ACCESS_KEY) == null) {
            throw new Exception("Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -PfullTests -DconnectionString=string" +
                    " -DblobAccountName=blob -DblobAccessKey=key");
        }
        properties.setProperty(CONNECTION_STRING, System.getProperty(CONNECTION_STRING));
        properties.setProperty(BLOB_ACCOUNT_NAME, System.getProperty(BLOB_ACCOUNT_NAME));
        properties.setProperty(BLOB_ACCESS_KEY, System.getProperty(BLOB_ACCESS_KEY));

        return properties;
    }

}
