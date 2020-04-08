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

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;

/**
 * SPI for loading routes/rests from XML input streams and parsing this to model definition classes.
 */
public interface XMLRoutesDefinitionLoader {

    /**
     * Service factory key.
     */
    String FACTORY = "xmlroutes-loader";

    /**
     * Loads from XML into routes.
     */
    Object loadRoutesDefinition(CamelContext context, InputStream inputStream) throws Exception;

    /**
     * Loads from XML into rests.
     */
    Object loadRestsDefinition(CamelContext context, InputStream inputStream) throws Exception;

    /**
     * Creates a model of the given type from the xml
     */
    <T extends NamedNode> T createModelFromXml(CamelContext context, String xml, Class<T> type) throws Exception;

}
