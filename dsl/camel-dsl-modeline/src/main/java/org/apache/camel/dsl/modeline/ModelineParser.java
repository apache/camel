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
package org.apache.camel.dsl.modeline;

import java.util.List;

import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.Resource;

/**
 * Modeline parser
 */
public interface ModelineParser {

    /**
     * Adds the {@link Trait} to the parser
     *
     * @param trait the trait
     */
    void addTrait(Trait trait);

    /**
     * Is the given source code line a modeline?
     *
     * @param  line the source code line
     * @return      <tt>true</tt> if modeline, <tt>false</tt> if not
     */
    boolean isModeline(String line);

    /**
     * Parses the resource to detect modelines and process them via {@link Trait}s
     *
     * @param  resource  the source code resource
     * @return           list of {@link CamelContextCustomizer} customizers that processes the modelines
     * @throws Exception is thrown if error during parsing
     */
    List<CamelContextCustomizer> parse(Resource resource) throws Exception;

    /**
     * A modeline was detected while parsing
     *
     * @param  resource the resource
     * @param  key      the mode line key
     * @param  value    the mode line value
     * @return          the trait that handles the detected modeline
     */
    Trait parseModeline(Resource resource, String key, String value);
}
