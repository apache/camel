package com.viwilo.camel.integration;

import java.util.Properties;
import org.apache.camel.test.junit4.CamelTestSupport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Created by thomas on 17/03/2017.
 */
public class DigitalOceanTestSupport extends CamelTestSupport {

    protected final Properties properties;

    protected DigitalOceanTestSupport() {
        URL url = getClass().getResource("/test-options.properties");

        InputStream inStream;
        try {
            inStream = url.openStream();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalAccessError("test-options.properties could not be found");
        }

        properties = new Properties();
        try {
            properties.load(inStream);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalAccessError("test-options.properties could not be found");
        }
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return properties;
    }

}
