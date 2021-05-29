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
package org.apache.camel.component.kamelet;

import org.apache.camel.spi.Resource;

/**
 * Listener when the Kamelet component is loaing Kamelet(s) from a {@link Resource}.
 *
 * For example when Kamelets are loaded from YAML files from the classpath, or via github from the Apache Camel Kamelet
 * Catalog.
 */
@FunctionalInterface
public interface KameletResourceLoaderListener {

    /**
     * About to load kamelets from the given resource.
     *
     * @param resource the resource that has kamelets to be loaded
     */
    void loadKamelets(Resource resource);
}
