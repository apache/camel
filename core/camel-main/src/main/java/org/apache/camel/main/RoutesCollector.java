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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;

/**
 * Collects routes and rests from the various sources (like registry or opinionated
 * classpath locations) and adds these into the Camel context.
 */
public interface RoutesCollector {

    /**
     * Collects the {@link RoutesBuilder} instances which was discovered from the {@link org.apache.camel.spi.Registry} such as
     * Spring or CDI bean containers.
     *
     * @param camelContext        the Camel Context
     * @param excludePattern      exclude pattern (see javaRoutesExcludePattern option)
     * @param includePattern      include pattern  (see javaRoutesIncludePattern option)
     * @return the discovered routes or an empty list
     */
    List<RoutesBuilder> collectRoutesFromRegistry(CamelContext camelContext, String excludePattern, String includePattern);

    /**
     * Collects all XML routes from the given directory.
     *
     * @param camelContext               the Camel Context
     * @param directory                  the directory (see xmlRoutes option)
     * @return the discovered routes or an empty list
     */
    List<RoutesDefinition> collectXmlRoutesFromDirectory(CamelContext camelContext, String directory) throws Exception;

    /**
     * Collects all XML rests from the given directory.
     *
     * @param camelContext               the Camel Context
     * @param directory                  the directory (see xmlRests option)
     * @return the discovered rests or an empty list
     */
    List<RestsDefinition> collectXmlRestsFromDirectory(CamelContext camelContext, String directory) throws Exception;

}
