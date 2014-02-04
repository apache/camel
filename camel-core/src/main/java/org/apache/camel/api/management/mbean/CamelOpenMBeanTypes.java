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
package org.apache.camel.api.management.mbean;

import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;

/**
 * Various JMX openmbean types used by Camel.
 */
public final class CamelOpenMBeanTypes {

    private CamelOpenMBeanTypes() {
    }

    public static TabularType listTypeConvertersTabularType() throws OpenDataException {
        CompositeType ct = listTypeConvertersCompositeType();
        return new TabularType("listTypeConverters", "Lists all the type converters in the registry (from -> to)", ct, new String[]{"from", "to"});
    }

    public static CompositeType listTypeConvertersCompositeType() throws OpenDataException {
        return new CompositeType("types", "From/To types", new String[]{"from", "to"},
                new String[]{"From type", "To type"}, new OpenType[]{SimpleType.STRING, SimpleType.STRING});
    }
}
