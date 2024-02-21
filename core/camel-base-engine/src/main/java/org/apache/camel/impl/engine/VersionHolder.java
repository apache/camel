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

package org.apache.camel.impl.engine;

import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.util.IOHelper;

class VersionHolder {
    public static final String VERSION;
    private static final String POM_PROPERTIES = "/META-INF/maven/org.apache.camel/camel-base-engine/pom.properties";

    static {
        VERSION = doGetVersion();
    }

    VersionHolder() {
    }

    private static String doGetVersion() {
        InputStream is = null;
        // try to load from maven properties first
        try {
            is = VersionHolder.class.getResourceAsStream(POM_PROPERTIES);
            if (is != null) {
                Properties p = new Properties();

                p.load(is);
                return p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (is != null) {
                IOHelper.close(is);
            }
        }

        // fallback to using Java API
        return fallbackResolver();
    }

    private static String fallbackResolver() {
        String resolvedVersion = null;

        Package aPackage = VersionHolder.class.getPackage();
        if (aPackage != null) {
            resolvedVersion = aPackage.getImplementationVersion();
            if (resolvedVersion == null) {
                resolvedVersion = aPackage.getSpecificationVersion();
            }
        }

        if (resolvedVersion == null) {
            // we could not compute the version so use a blank
            resolvedVersion = "";
        }

        return resolvedVersion;
    }
}
