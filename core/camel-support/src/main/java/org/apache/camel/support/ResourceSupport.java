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
package org.apache.camel.support;

import java.util.Objects;

import org.apache.camel.spi.Resource;

/**
 * Base class for {@link Resource} implementations.
 */
public abstract class ResourceSupport implements Resource {
    private final String scheme;
    private final String location;

    protected ResourceSupport(String scheme, String location) {
        this.scheme = scheme;
        this.location = location;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String toString() {
        String prefix = scheme + ":";
        if (location.startsWith(prefix)) {
            return "Resource[" + location + "]";
        } else {
            return "Resource[" + prefix + location + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResourceSupport that = (ResourceSupport) o;
        return scheme.equals(that.scheme) && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, location);
    }
}
