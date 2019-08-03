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
package org.apache.camel.impl.scan;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.spi.PackageScanFilter;

/**
 * <code>CompositePackageScanFilter</code> allows multiple
 * {@link PackageScanFilter}s to be composed into a single filter. For a
 * {@link Class} to match a {@link CompositePackageScanFilter} it must match
 * each of the filters the composite contains
 */
public class CompositePackageScanFilter implements PackageScanFilter {
    private Set<PackageScanFilter> filters;

    public CompositePackageScanFilter() {
        filters = new LinkedHashSet<>();
    }

    public CompositePackageScanFilter(Set<PackageScanFilter> filters) {
        this.filters = new LinkedHashSet<>(filters);
    }

    public void addFilter(PackageScanFilter filter) {
        filters.add(filter);
    }

    @Override
    public boolean matches(Class<?> type) {
        for (PackageScanFilter filter : filters) {
            if (!filter.matches(type)) {
                return false;
            }
        }
        return true;
    }
}
