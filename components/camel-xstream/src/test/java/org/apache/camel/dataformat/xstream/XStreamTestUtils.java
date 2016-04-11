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
package org.apache.camel.dataformat.xstream;

/**
 * 
 */
final class XStreamTestUtils {
    static final String PERMISSIONS_PROPERTY_KEY = "org.apache.camel.xstream.permissions";  
    private static String oldProperty;

    private XStreamTestUtils() {
    }

    public static void setPermissionSystemProperty(String value) {
        oldProperty = System.getProperty(PERMISSIONS_PROPERTY_KEY);
        if (value == null) {
            System.clearProperty(PERMISSIONS_PROPERTY_KEY);
        } else {
            System.setProperty(PERMISSIONS_PROPERTY_KEY, value);
        }
    }

    public static void revertPermissionSystemProperty() {
        if (oldProperty == null) {
            System.clearProperty(PERMISSIONS_PROPERTY_KEY);
        } else {
            System.setProperty(PERMISSIONS_PROPERTY_KEY, oldProperty);
        }
    }
}
