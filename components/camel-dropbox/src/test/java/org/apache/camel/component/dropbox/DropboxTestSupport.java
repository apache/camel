package org.apache.camel.component.dropbox;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IntrospectionSupport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/23/13
 * Time: 6:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class DropboxTestSupport extends CamelTestSupport {

    protected final Properties properties;

    protected DropboxTestSupport() throws Exception {
        URL url = getClass().getResource("/test-options.properties");

        InputStream inStream;
        try {
            inStream = url.openStream();
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new IllegalAccessError("test-options.properties could not be found");
        }

        properties = new Properties();
        try {
            properties.load(inStream);
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new IllegalAccessError("test-options.properties could not be found");
        }

        Map<String, Object> options = new HashMap<String, Object>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            options.put(entry.getKey().toString(), entry.getValue());
        }
    }

    protected String getAuthParams() {
        return "appKey=" + properties.get("appKey")
                + "&appSecret=" + properties.get("appSecret")
                + "&accessToken=" + properties.get("accessToken");
    }
}
