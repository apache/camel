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
package org.apache.camel.spi;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;

/**
 * Allows SPI to plugin a {@link RestApiProcessorFactory} that creates the Camel {@link Processor} responsible
 * for servicing and generating the REST API documentation.
 * <p/>
 * For example the <tt>camel-swagger-java</tt> component provides such a factory that uses Swagger to generate the documentation.
 */
public interface RestApiProcessorFactory {

    /**
     * Creates a new REST API <a
     * href="http://camel.apache.org/processor.html">Processor
     * </a>, which provides API listing of the REST services
     *
     * @param camelContext      the camel context
     * @param contextPath       the context-path
     * @param contextIdPattern  id pattern to only allow Rest APIs from rest services within CamelContext's which name matches the pattern.
     * @param parameters        additional parameters
     * @return a newly created REST API provider
     * @throws Exception can be thrown
     */
    Processor createApiProcessor(CamelContext camelContext, String contextPath, String contextIdPattern, boolean contextIdListing,
                                 RestConfiguration configuration, Map<String, Object> parameters) throws Exception;

}
