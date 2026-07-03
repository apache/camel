/*
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
package org.apache.camel.catalog;

import java.io.InputStream;
import java.util.Properties;

/**
 * To get the version of this catalog.
 */
public class VersionHelper {

    private static volatile String version;

    public synchronized String getVersion() {
        if (version != null) {
            return version;
        }
        // First, try to load from maven properties
        version = getVersionFromProperties("/META-INF/maven/org.apache.camel/camel-catalog/pom.properties");

        // Next, try to load from version.properties
        if (version == null) {
            version = getVersionFromProperties("/META-INF/version.properties");
        }

        // Fallback to using Java API
        if (version == null) {
            Package aPackage = getClass().getPackage();
            if (aPackage != null) {
                version = aPackage.getImplementationVersion();
                if (version == null) {
                    version = aPackage.getSpecificationVersion();
                }
            }
        }

        if (version == null) {
            // we could not compute the version so use a blank
            version = "";
        }

        return version;
    }

    private String getVersionFromProperties(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            Properties p = new Properties();
            if (is != null) {
                p.load(is);
                return p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }
}
