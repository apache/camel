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
package org.apache.camel.spi;

/**
 * Predicate applied by {@link PackageScanClassResolver} during classpath scanning to determine whether a discovered
 * class should be included in the result set.
 * <p/>
 * Implementations receive each candidate class and return {@code true} to include it or {@code false} to skip it.
 * Because this is a {@link FunctionalInterface}, a filter can be expressed as a lambda or method reference:
 *
 * <pre>
 * resolver.findByFilter(cls -&gt; cls.isAnnotationPresent(MyAnnotation.class), "com.example");
 * </pre>
 *
 * Global filters registered via {@link PackageScanClassResolver#addFilter(PackageScanFilter)} apply to every subsequent
 * scan; per-query filters passed to {@link PackageScanClassResolver#findByFilter} apply only to that single call.
 *
 * @see PackageScanClassResolver
 */
@FunctionalInterface
public interface PackageScanFilter {

    /**
     * Does the given class match
     *
     * @param  type the class
     * @return      true to include this class, false to skip it.
     */
    boolean matches(Class<?> type);
}
