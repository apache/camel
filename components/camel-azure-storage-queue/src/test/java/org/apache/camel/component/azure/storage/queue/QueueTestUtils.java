package org.apache.camel.component.azure.storage.queue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public final class QueueTestUtils {

    private QueueTestUtils() {
    }

    public static Properties loadAzurePropertiesFile() throws IOException {
        final Properties properties = new Properties();
        final String fileName = "azure_key.properties";

        final InputStream inputStream = Objects.requireNonNull(QueueTestUtils.class.getClassLoader().getResourceAsStream(fileName));

        properties.load(inputStream);

        return properties;
    }

    public static Properties loadAzureAccessFromJvmEnv() throws Exception {
        final Properties properties = new Properties();
        if (System.getProperty("accountName") == null || System.getProperty("accessKey") == null) {
            throw new Exception("Make sure to supply azure accessKey or accountName, e.g:  mvn verify -PfullTests -DaccountName=myacc -DaccessKey=mykey");
        }
        properties.setProperty("account_name", System.getProperty("accountName"));
        properties.setProperty("access_key", System.getProperty("accessKey"));

        return properties;
    }

}
