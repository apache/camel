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
package org.apache.camel.util;

import java.lang.reflect.Method;

import static java.lang.Thread.currentThread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility dedicated for resolving runtime information related to the platform on which Camel is currently running.
 */
public final class PlatformHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformHelper.class);

    private PlatformHelper() {
    }

    /**
     * Determine whether Camel is running in the OSGi environment. Current implementation tries to load Camel activator
     * bundle (using reflection API and class loading) to determine if the code is executed in the OSGi environment.
     *
     * @param classLoader caller class loader to be used to load Camel Bundle Activator
     * @return true if caller is running in the OSGi environment, false otherwise
     */
    public static boolean isInOsgiEnvironment(ClassLoader classLoader) {
        try {
            // Try to load the BundleActivator first
            Class.forName("org.osgi.framework.BundleActivator");
            Class<?> activatorClass = classLoader.loadClass("org.apache.camel.impl.osgi.Activator");
            Method getBundleMethod = activatorClass.getDeclaredMethod("getBundle");
            Object bundle = getBundleMethod.invoke(null);
            return bundle != null;
        } catch (Throwable t) {
            LOG.trace("Cannot find class so assuming not running in OSGi container: " + t.getMessage());
            return false;
        }
    }

    public static boolean isInOsgiEnvironment() {
        return isInOsgiEnvironment(PlatformHelper.class.getClassLoader());
    }

}
