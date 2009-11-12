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
package org.apache.camel.impl.scan;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.spi.PackageScanFilter;

/**
 * Package scan filter for testing if a given class is assignable to another class.
 */
public class AssignableToPackageScanFilter implements PackageScanFilter {
    private final Set<Class<?>> parents = new HashSet<Class<?>>();

    public AssignableToPackageScanFilter() {
    }

    public AssignableToPackageScanFilter(Class<?> parentType) {
        parents.add(parentType);
    }

    public AssignableToPackageScanFilter(Set<Class<?>> parents) {
        this.parents.addAll(parents);
    }

    public void addParentType(Class<?> parentType) {
        parents.add(parentType);
    }

    public boolean matches(Class<?> type) {
        if (parents != null && parents.size() > 0) {
            for (Class<?> parent : parents) {
                if (parent.isAssignableFrom(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Class<?> parent : parents) {
            sb.append(parent.getSimpleName()).append(", ");
        }
        sb.setLength(sb.length() > 0 ? sb.length() - 2 : 0);
        return "is assignable to any of " + sb;
    }
}
