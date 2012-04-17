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
package org.apache.camel.web.util;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.core.PackagesResourceConfig;
import org.apache.camel.web.resources.Constants;

/**
 *
 */
public class CamelResourceConfig extends PackagesResourceConfig {

    public CamelResourceConfig() {
        this("org.apache.camel.web");
    }

    public CamelResourceConfig(String packages) {
        super(createProperties(packages));
    }

    protected static Map<String, Object> createProperties(String packages) {
        Map<String, Object> properties = new HashMap<String, Object>();

        properties.put(PackagesResourceConfig.PROPERTY_PACKAGES, packages);

/*
        WadlGeneratorConfig config = WadlGeneratorConfig
                .generator(WadlGeneratorApplicationDoc.class)
                .prop("applicationDocsFile", "classpath:/application-doc.xml")
                .generator(WadlGeneratorGrammarsSupport.class)
                .prop("grammarsFile", "classpath:/application-grammars.xml")
                .generator(WadlGeneratorResourceDocSupport.class)
                .prop("resourceDocFile", "classpath:/resourcedoc.xml")
                .build();

        properties.put(ResourceConfig.PROPERTY_WADL_GENERATOR_CONFIG, config);
*/
        return properties;
    }

    public Map<String, MediaType> getMediaTypeMappings() {
        Map<String, MediaType> m = new HashMap<String, MediaType>();
        m.put("html", MediaType.TEXT_HTML_TYPE);
        m.put("xml", MediaType.APPLICATION_XML_TYPE);
        m.put("json", MediaType.APPLICATION_JSON_TYPE);
        m.put("dot", MediaType.valueOf(Constants.DOT_MIMETYPE));
        return m;
    }
}