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

import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;

/**
 * SPI for loading resources.
 */
public interface ResourceResolver extends StaticService, CamelContextAware {

    /**
     * Service factory base path for scheme specific resolver.
     */
    String FACTORY_PATH = "META-INF/services/org/apache/camel/resource-resolver/";

    /**
     * The supported resource scheme.
     * <p/>
     * Implementations should support a single scheme only.
     */
    String getSupportedScheme();

    /**
     * Resolve a {@link Resource} from a give uri.
     *
     * @param  location the location of the resource to resolve.
     * @return          an {@link Resource}, null if was not possible to resolve the resource.
     */
    Resource resolve(String location);
}
