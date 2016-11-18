package org.apache.camel.component.firebase.provider;

import org.apache.camel.component.firebase.FirebaseConfig;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

import static org.junit.Assert.assertNotNull;

/**
 * Provides the path of the configuration used to access Firebase.
 */
public class ConfigurationProvider {

    public static String createFirebaseConfigLink() throws URISyntaxException, UnsupportedEncodingException {
        URL url = Thread.currentThread().getContextClassLoader().getResource("gil-sample-app-firebase-adminsdk-rcwg7-fea519a672.json");
        assertNotNull(url);
        File f = new File(url.toURI());
        return URLEncoder.encode(f.getAbsolutePath(), "UTF-8");
    }

    public static FirebaseConfig createDemoConfig() throws IOException, URISyntaxException {
        FirebaseConfig firebaseConfig = new FirebaseConfig.Builder(String.format("https://%s", createDatabaseUrl()), createRootReference(),
                URLDecoder.decode(createFirebaseConfigLink(), "UTF-8")).build();
        firebaseConfig.init();
        return firebaseConfig;
    }

    public static String createRootReference() {
        return "server/saving-data";
    }

    public static String createDatabaseUrl() {
        return "gil-sample-app.firebaseio.com";
    }
}
