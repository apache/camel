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
package org.apache.camel.component.dropbox.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public final class DropboxPropertyManager {

    private static Properties properties;
    private static DropboxPropertyManager instance;

    private DropboxPropertyManager() { }

    public static synchronized DropboxPropertyManager getInstance() throws Exception {
        if (instance == null) {
            instance = new DropboxPropertyManager();
            properties = loadProperties();
        }
        return instance;
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }


    private static Properties loadProperties() throws Exception {
        URL url = DropboxPropertyManager.class.getResource("/dropbox.properties");
        InputStream inStream;
        try {
            inStream = url.openStream();
        } catch (IOException e) {
            e.printStackTrace();
            throw new DropboxException("dropbox.properties could not be found");
        }
        properties = new Properties();
        try {
            properties.load(inStream);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DropboxException("dropbox.properties can't be read");
        }
        return properties;
    }
}
