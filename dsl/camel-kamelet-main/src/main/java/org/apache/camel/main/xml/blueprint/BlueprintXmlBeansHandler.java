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
package org.apache.camel.main.xml.blueprint;

import java.util.Map;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.main.xml.spring.SpringXmlBeansHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used for parsing and discovering legacy OSGi <blueprint> XML to make it runnable on camel-jbang, and for tooling to
 * migrate this to modern Camel DSL in plain Camel XML or YAML DSL.
 */
public class BlueprintXmlBeansHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SpringXmlBeansHandler.class);

    /**
     * Parses the XML documents and discovers blueprint beans, which will be created manually via Camel.
     */
    public void processBlueprintBeans(
            CamelContext camelContext, MainConfigurationProperties config, final Map<String, Document> xmls) {

        LOG.debug("Loading beans from classic OSGi <blueprint> XML");
    }

    /**
     * Invoked at later stage to create and register Blueprint beans into Camel {@link org.apache.camel.spi.Registry}.
     */
    public void createAndRegisterBeans(CamelContext camelContext) {

    }

}
