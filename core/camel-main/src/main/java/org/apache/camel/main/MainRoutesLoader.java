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
package org.apache.camel.main;

import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.spi.ExtendedRoutesBuilderLoader;
import org.apache.camel.spi.RoutesBuilderLoader;

/**
 * Main {@link org.apache.camel.spi.RoutesLoader}.
 */
public class MainRoutesLoader extends DefaultRoutesLoader {

    private final MainConfigurationProperties configuration;

    public MainRoutesLoader(MainConfigurationProperties configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initRoutesBuilderLoader(RoutesBuilderLoader loader) {
        // configure extended routes loader options
        if (loader instanceof ExtendedRoutesBuilderLoader) {
            ExtendedRoutesBuilderLoader ext = (ExtendedRoutesBuilderLoader) loader;
            if (configuration.getRoutesCompileDirectory() != null) {
                ext.setCompileDirectory(configuration.getRoutesCompileDirectory());
            }
            ext.setCompileLoadFirst(configuration.isRoutesCompileLoadFirst());
        }
    }

}
