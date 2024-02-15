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
package org.apache.camel.dsl.yaml;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed CamelK YAML RoutesBuilderLoader")
@RoutesLoader(CamelKYamlRoutesBuilderLoader.EXTENSION)
@Deprecated
public class CamelKYamlRoutesBuilderLoader extends YamlRoutesBuilderLoader {

    private static final Logger LOG = LoggerFactory.getLogger(CamelKYamlRoutesBuilderLoader.class);

    public static final String EXTENSION = "camelk.yaml";

    public CamelKYamlRoutesBuilderLoader() {
        super(EXTENSION);
        LOG.warn("Camel routes in files with extension .camelk.yaml is deprecated. Use .camel.yaml instead.");
    }
}
