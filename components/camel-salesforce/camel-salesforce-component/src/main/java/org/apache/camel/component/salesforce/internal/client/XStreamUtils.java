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

package org.apache.camel.component.salesforce.internal.client;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;
import com.thoughtworks.xstream.security.ExplicitTypePermission;
import com.thoughtworks.xstream.security.TypePermission;
import com.thoughtworks.xstream.security.WildcardTypePermission;

/**
 * REVISIT this code is duplicated from camel-xstream and we should
 * find another way...
 */
public final class XStreamUtils {
    private static final String PERMISSIONS_PROPERTY_KEY = "org.apache.camel.xstream.permissions";
    private static final String PERMISSIONS_PROPERTY_DEFAULT = "java.lang.*,java.util.*";

    private XStreamUtils() {
    }

    public static void addPermissions(XStream xstream, String permissions) {
        for (String pterm : permissions.split(",")) {
            boolean aod;
            pterm = pterm.trim();
            if (pterm.startsWith("-")) {
                aod = false;
                pterm = pterm.substring(1);
            } else {
                aod = true;
                if (pterm.startsWith("+")) {
                    pterm = pterm.substring(1);
                }
            }
            TypePermission typePermission = null;
            if ("*".equals(pterm)) {
                // accept or deny any
                typePermission = AnyTypePermission.ANY;
            } else if (pterm.indexOf('*') < 0) {
                // exact type
                typePermission = new ExplicitTypePermission(new String[]{pterm});
            } else if (pterm.length() > 0) {
                // wildcard type
                typePermission = new WildcardTypePermission(new String[]{pterm});
            }
            if (typePermission != null) {
                if (aod) {
                    xstream.addPermission(typePermission);
                } else {
                    xstream.denyPermission(typePermission);
                }
            }
        }
    }

    public static void addDefaultPermissions(XStream xstream) {
        addPermissions(xstream, System.getProperty(PERMISSIONS_PROPERTY_KEY, PERMISSIONS_PROPERTY_DEFAULT));
    }
}
