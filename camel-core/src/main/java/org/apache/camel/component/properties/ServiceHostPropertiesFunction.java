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
package org.apache.camel.component.properties;

import java.util.Locale;

import org.apache.camel.util.ObjectHelper;

/**
 * A {@link PropertiesFunction} that lookup the property value from
 * OS environment variables using the service idiom.
 * <p/>
 * A service is defined using two environment variables where name is name of the service:
 * <ul>
 *   <li><tt>NAME_SERVICE_HOST</tt></li>
 *   <li><tt>NAME_SERVICE_PORT</tt></li>
 * </ul>
 * in other words the service uses <tt>_SERVICE_HOST</tt> and <tt>_SERVICE_PORT</tt> as prefix.
 * <p/>
 * This implementation is to return the host part only.
 *
 * @see ServicePropertiesFunction
 * @see ServicePortPropertiesFunction
 */
public class ServiceHostPropertiesFunction implements PropertiesFunction {

    private static final String HOST_PREFIX = "_SERVICE_HOST";

    @Override
    public String getName() {
        return "service.host";
    }

    @Override
    public String apply(String remainder) {
        String key = remainder;
        String defaultValue = null;

        if (remainder.contains(":")) {
            key = ObjectHelper.before(remainder, ":");
            defaultValue = ObjectHelper.after(remainder, ":");
        }

        // make sure to use upper case
        if (key != null) {
            // make sure to use underscore as dash is not supported as ENV variables
            key = key.toUpperCase(Locale.ENGLISH).replace('-', '_');

            // a service should have both the host and port defined
            String host = System.getenv(key + HOST_PREFIX);

            if (host != null) {
                return host;
            } else {
                return defaultValue;
            }
        }

        return defaultValue;
    }
}

