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
package org.apache.camel.component.avro;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.CamelTestSupport;

public class AvroTestSupport extends CamelTestSupport {


    public static int setupFreePort(String name) {
        int port = -1;
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            Properties properties = new Properties();
            File propertiesFile = new File("target/custom.properties");
            if (!propertiesFile.exists()) {
                propertiesFile.createNewFile();
            }
            fis = new FileInputStream(propertiesFile);
            fos = new FileOutputStream(propertiesFile);
            properties.load(fis);
            if (properties.contains(name)) {
                return Integer.parseInt((String) properties.get(name));
            } else {
                // find a free port number from 9100 onwards, and write that in the custom.properties file
                // which we will use for the unit tests, to avoid port number in use problems
                port = AvailablePortFinder.getNextAvailable(9100);
                properties.put(name, String.valueOf(port));
                properties.store(fos, "avro");
            }
        } catch (IOException e) {
            //Ignore
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception ex) {
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ex) {
                }
            }
        }
        return port;
    }

}
