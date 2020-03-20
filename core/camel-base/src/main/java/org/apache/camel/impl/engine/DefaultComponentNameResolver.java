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

import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ComponentNameResolver;

public class DefaultComponentNameResolver implements ComponentNameResolver {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/component/*";

    @Override
    public Set<String> resolveNames(CamelContext camelContext) {
        // remove leading path to only keep name
        Set<String> sorted = new TreeSet<>();

        try {
            Set<String> locations = camelContext.adapt(ExtendedCamelContext.class).getPackageScanResourceResolver().findResourceNames(RESOURCE_PATH);
            locations.forEach(l -> sorted.add(l.substring(l.lastIndexOf('/') + 1)));
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }

        return sorted;
    }
}
