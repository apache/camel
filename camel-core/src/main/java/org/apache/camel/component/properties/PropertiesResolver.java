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
package org.apache.camel.component.properties;

import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;

/**
 * A resolver to load properties from a given source such as a file from a classpath.
 * <p/>
 * Implementations can also load properties from another source source as JNDI.
 *
 * @version 
 */
public interface PropertiesResolver {

    /**
     * Resolve properties from the given uri
     *
     * @param context the camel context
     * @param ignoreMissingLocation ignore silently if the property file is missing
     * @param locations location(s) defining the source(s)
     * @return the properties
     * @throws Exception is thrown if resolving the properties failed
     */
    Properties resolveProperties(CamelContext context, boolean ignoreMissingLocation, List<PropertiesLocation> locations) throws Exception;
}
