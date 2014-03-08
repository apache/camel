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
package org.apache.camel.bam;

import javax.persistence.Entity;

public final class QueryUtils {

    private QueryUtils() {
    }

    public static String getTypeName(Class<?> clazz) {

        if (clazz == null) {
            return null;
        } else {

            // Check if we have a property name on the @Entity annotation
            String name = getEntityName(clazz);

            if (name != null) {
                return name;
            } else {
                // Remove package name of the entity to be conform with JPA 1.0
                // spec
                return clazz.getSimpleName();
            }

        }
    }

    protected static String getEntityName(Class<?> clazz) {

        Entity entity = clazz.getAnnotation(Entity.class);

        // Check if the property name has been defined for Entity annotation
        if (!entity.name().equals("")) {
            return entity.name();
        } else {
            return null;
        }

    }

}
