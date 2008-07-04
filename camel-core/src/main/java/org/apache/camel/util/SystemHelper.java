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

import org.apache.camel.converter.ObjectConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A helper class for various {@link System} related methods
 *
 * @version $Revision$
 */
public final class SystemHelper {
    private static final transient Log LOG = LogFactory.getLog(SystemHelper.class);

    private SystemHelper() {
        // Helper class
    }

    /**
     * Looks up the given system property name returning null if any exceptions occur
     */
    public static String getSystemProperty(String name) {
        try {
            return System.getProperty(name);
        } catch (Exception e) {
            LOG.debug("Caught exception looking for system property: " + name + " exception: " + e, e);
            return null;
        }
    }

    /**
     * Looks up the given system property boolean value. Returns false if the system property doesn't exist.
     */
    public static boolean isSystemProperty(String name) {
        String text = getSystemProperty(name);
        return ObjectConverter.toBool(text);
    }

}
