package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class BlobTestUtils {

    public static Properties loadAzurePropertiesFile() throws IOException {
        final Properties properties = new Properties();
        final String fileName = "azure_key.properties";

        final InputStream inputStream = Objects.requireNonNull(BlobTestUtils.class.getClassLoader().getResourceAsStream(fileName));

        properties.load(inputStream);

        return properties;
    }

}
