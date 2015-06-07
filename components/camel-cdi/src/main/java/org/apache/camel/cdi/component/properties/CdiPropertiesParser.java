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
package org.apache.camel.cdi.component.properties;

import java.util.Properties;

import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.deltaspike.core.api.config.ConfigResolver;

/**
 * Properties parser which uses {@link ConfigResolver} from deltaspike to obtain
 * configuration properties. If property is not recognized by deltaspike execution
 * will be delegated to parent implementation.
 */
public class CdiPropertiesParser extends DefaultPropertiesParser {

    public CdiPropertiesParser(PropertiesComponent propertiesComponent) {
        super(propertiesComponent);
    }

    @Override
    public String parseProperty(String key, String value, Properties properties) {
        String answer = ConfigResolver.getPropertyValue(key);

        if (answer != null) {
            return answer;
        }

        return super.parseProperty(key, value, properties);
    }

}
