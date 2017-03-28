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

import org.apache.camel.util.ObjectHelper;

public class PropertiesLocation {
    private final String resolver;
    private final String path;
    private final boolean optional;

    public PropertiesLocation(String location) {
        // make sure to trim as people may use new lines when configuring using XML
        // and do this in the setter as Spring/Blueprint resolves placeholders before
        // Camel is being started
        location = location.trim();

        int idx = location.indexOf(':');
        if (idx != -1) {
            this.resolver = location.substring(0, idx);
            location = location.substring(idx + 1);
        } else {
            this.resolver = "classpath";
        }

        idx = location.lastIndexOf(';');
        if (idx != -1) {
            this.optional = ObjectHelper.after(location.substring(idx + 1), "optional=", Boolean::valueOf).orElse(false);
            location = location.substring(0, idx);
        } else {
            this.optional = false;
        }

        this.path = location;
    }

    public PropertiesLocation(String resolver, String path) {
        this(resolver, path, false);
    }

    public PropertiesLocation(String resolver, String path, Boolean optional) {
        this.resolver = resolver;
        this.path = path;
        this.optional = optional;
    }

    // *****************************
    // Getters
    // *****************************

    public String getResolver() {
        return resolver;
    }

    public String getPath() {
        return path;
    }

    public boolean isOptional() {
        return optional;
    }

    // *****************************
    // Equals/HashCode/ToString
    // *****************************

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PropertiesLocation location = (PropertiesLocation) o;

        if (optional != location.optional) {
            return false;
        }
        if (resolver != null ? !resolver.equals(location.resolver) : location.resolver != null) {
            return false;
        }
        return path != null ? path.equals(location.path) : location.path == null;
    }

    @Override
    public int hashCode() {
        int result = resolver != null ? resolver.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (optional ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PropertiesLocation{"
            + "resolver='" + resolver + '\''
            + ", path='" + path + '\''
            + ", optional=" + optional
            + '}';
    }
}
