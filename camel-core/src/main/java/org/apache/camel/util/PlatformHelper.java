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

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.osgi.framework.FrameworkUtil.getBundle;

/**
 * Utility dedicated for resolving runtime information related to the platform on which Camel is currently running.
 */
public final class PlatformHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformHelper.class);

    private PlatformHelper() {
    }

    /**
     * Determine whether Camel is running in the OSGi environment.
     *
     * @param classFromBundle class to be tested against being deployed into OSGi
     * @return true if caller is running in the OSGi environment, false otherwise
     */
    public static boolean isInOsgiEnvironment(Class classFromBundle) {
        Bundle bundle = getBundle(classFromBundle);
        if (bundle != null) {
            LOG.trace("Found OSGi bundle {} for class {} so assuming running in the OSGi container.",
                    bundle.getSymbolicName(), classFromBundle.getSimpleName());
            return true;
        } else {
            LOG.trace("Cannot find OSGi bundle for class {} so assuming not running in the OSGi container.",
                    classFromBundle.getSimpleName());
            return false;
        }
    }

    public static boolean isInOsgiEnvironment() {
        return isInOsgiEnvironment(PlatformHelper.class);
    }

}
