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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesLookup;
import org.apache.camel.support.component.PropertyConfigurerSupport;

/**
 * During export then we can be more flexible and allow missing property placeholders to resolve to a hardcoded value,
 * so we can keep attempting to export.
 */
public class ExportPropertiesParser extends DefaultPropertiesParser {

    private final CamelContext camelContext;

    public ExportPropertiesParser(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String parseProperty(String key, String value, PropertiesLookup properties) {
        if (value == null) {
            // the key may refer to a properties function so make sure we include this during export
            if (key != null) {
                try {
                    camelContext.getPropertiesComponent().getPropertiesFunction(key);
                } catch (Exception e) {
                    // ignore
                }
            }
            return PropertyConfigurerSupport.MAGIC_VALUE;
        }
        return value;
    }

}
