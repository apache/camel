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
 * Factory for parsing camel-k modeline when running Camel standalone with DSLs.
 */
public interface ModelineFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "dsl-modeline-factory";

    /**
     * Parses the resources to discover camel-k modeline snippets which is parsed and processed.
     *
     * @param  resource  the resource with Camel routes such as a yaml, xml or java source file.
     * @throws Exception is thrown if error parsing
     */
    void parseModeline(Resource resource) throws Exception;

}
