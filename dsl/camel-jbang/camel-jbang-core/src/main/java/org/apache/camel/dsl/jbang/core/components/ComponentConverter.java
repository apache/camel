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
package org.apache.camel.dsl.jbang.core.components;

import java.util.regex.Matcher;

import org.apache.camel.dsl.jbang.core.api.Converter;
import org.apache.camel.dsl.jbang.core.types.Component;

public class ComponentConverter implements Converter<Component> {

    @Override
    public Component convert(Matcher matcher) {
        Component component = new Component();

        component.name = matcher.group(2).replace(".adoc", "");
        component.shortName = component.name.replace("-component", "");
        component.description = matcher.group(3);
        component.link = String.format("https://camel.apache.org/components/latest/%s.html", component.name);

        return component;
    }
}
