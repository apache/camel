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
package org.apache.camel.swagger;

import java.util.Map;

import io.swagger.converter.ModelConverters;
import io.swagger.models.Model;
import io.swagger.models.properties.StringProperty;

/**
 * A Camel extended {@link ModelConverters} where we appending vendor extensions
 * to include the java class name of the model classes.
 */
public class RestModelConverters extends ModelConverters {

    public Map<String, Model> readClass(Class clazz) {
        String name = clazz.getName();
        Map<String, Model> resolved = super.read(clazz);
        if (resolved != null) {
            for (Model model : resolved.values()) {
                // enrich with the class name of the model
                model.getVendorExtensions().put("x-className", new StringProperty(name));
            }

            // read any extra using read-all
            Map<String, Model> extra = super.readAll(clazz);
            if (extra != null) {
                for (Map.Entry<String, Model> entry : extra.entrySet()) {
                    if (!resolved.containsKey(entry.getKey())) {
                        resolved.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return resolved;
    }
}
