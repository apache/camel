/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.facebook;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.camel.component.facebook.config.FacebookConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IntrospectionSupport;

public abstract class CamelFacebookTestSupport extends CamelTestSupport {

    protected final Properties properties;
    protected final FacebookConfiguration configuration;

    protected CamelFacebookTestSupport() throws Exception {
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

        Map<String, Object> options = new HashMap<String, Object>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            options.put(entry.getKey().toString(), entry.getValue());
        }

        configuration = new FacebookConfiguration();
        IntrospectionSupport.setProperties(configuration, options);
    }

    protected FacebookConfiguration getConfiguration() {
        return configuration;
    }

    protected String getOauthParams() {
        return "oAuthAppId=" + properties.get("oAuthAppId") + "&oAuthAppSecret=" + properties.get("oAuthAppSecret")
            + (properties.get("oAuthAccessToken") != null
            ? ("&oAuthAccessToken=" + properties.get("oAuthAccessToken")) : "");
    }
    
    protected String getAppOauthParams() {
        return "oAuthAppId=" + properties.get("oAuthAppId")
             + "&oAuthAppSecret=" + properties.get("oAuthAppSecret");
    }

    protected String getShortName(String name) {
        if (name.startsWith("get")) {
            name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
        } else if (name.startsWith("search") && !"search".equals(name)) {
            name = Character.toLowerCase(name.charAt(6)) + name.substring(7);
        }
        return name;
    }

}
