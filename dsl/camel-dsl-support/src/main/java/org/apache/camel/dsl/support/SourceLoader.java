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
package org.apache.camel.dsl.support;

import java.io.IOException;

import org.apache.camel.spi.Resource;

/**
 * Loader for loading the source code from {@link Resource}.
 *
 * Custom {@link SourceLoader} implementations can be plugged into the {@link org.apache.camel.CamelContext} by adding
 * to the {@link org.apache.camel.spi.Registry}.
 */
public interface SourceLoader {

    /**
     * Loads the source from the given resource
     *
     * @param  resource    the resource
     * @return             the source code (such as java, xml, groovy, yaml)
     *
     * @throws IOException is thrown if error loading the source
     */
    String loadResource(Resource resource) throws IOException;

}
