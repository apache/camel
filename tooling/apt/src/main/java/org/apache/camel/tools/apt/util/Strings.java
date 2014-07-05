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
package org.apache.camel.tools.apt.util;

/**
 * Some String helper methods
 */
public final class Strings {
    private Strings() {
        //Helper class
    }
    /**
     * Returns true if the given text is null or empty string
     */
    public static boolean isNullOrEmpty(String text) {
        return text == null || text.length() == 0;
    }

    /**
     * Returns the value or the defaultValue if it is null
     */
    public static String getOrElse(String text, String defaultValue) {
        return (text != null) ? text : defaultValue;
    }

    /**
     * Returns the canonical class name by removing any generic type information.
     */
    public static String canonicalClassName(String className) {
        // remove generics
        int pos = className.indexOf('<');
        if (pos != -1) {
            return className.substring(0, pos);
        } else {
            return className;
        }
    }
}
