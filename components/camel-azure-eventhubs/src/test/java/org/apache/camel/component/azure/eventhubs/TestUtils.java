package org.apache.camel.component.azure.eventhubs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class TestUtils {

    private TestUtils(){
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
        if (System.getProperty("connectionString") == null) {
            throw new Exception("Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -PfullTests -DconnectionString=string");
        }
        properties.setProperty("connectionString", System.getProperty("connectionString"));

        return properties;
    }

}
