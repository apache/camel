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

package org.apache.camel.dsl.jbang.core.common;

import java.util.Collection;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public final class YamlHelper {

    private YamlHelper() {}

    /**
     * Creates new Yaml instance. The implementation provided by Snakeyaml is not thread-safe. It is better to create a
     * fresh instance for every YAML stream.
     */
    public static Yaml yaml() {
        return yaml(null);
    }

    /**
     * Creates new Yaml instance. The implementation provided by Snakeyaml is not thread-safe. It is better to create a
     * fresh instance for every YAML stream. Uses the given class loader as base constructor. This is mandatory when
     * additional classes have been downloaded via Maven for instance when loading a Camel JBang plugin.
     */
    public static Yaml yaml(ClassLoader classLoader) {
        Representer representer = new Representer(new DumperOptions()) {
            @Override
            protected NodeTuple representJavaBeanProperty(
                    Object javaBean, Property property, Object propertyValue, Tag customTag) {
                // if value of property is null, ignore it.
                if (propertyValue == null
                        || (propertyValue instanceof Collection && ((Collection<?>) propertyValue).isEmpty())
                        || (propertyValue instanceof Map && ((Map<?, ?>) propertyValue).isEmpty())) {
                    return null;
                } else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
        };
        representer.getPropertyUtils().setSkipMissingProperties(true);

        if (classLoader != null) {
            return new Yaml(new CustomClassLoaderConstructor(classLoader, new LoaderOptions()), representer);
        }

        return new Yaml(representer);
    }
}
