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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.util.ObjectHelper;

/**
 * Default data format resolver
 *
 * @version $Revision$
 */
public class DefaultDataFormatResolver implements DataFormatResolver {

    public DataFormat resolveDataFormatByClassName(String name, CamelContext context) {
        if (name != null) {
            Class type = context.getClassResolver().resolveClass(name);
            if (type == null) {
                throw new IllegalArgumentException("The class " + name + " is not on the classpath! Cannot use the dataFormat " + this);
            }
            return (DataFormat) ObjectHelper.newInstance(type);
        }
        return null;
    }

    public DataFormat resolveDataFormatByRef(String ref, CamelContext context) {
        DataFormat dataFormat = lookup(context, ref, DataFormat.class);
        if (dataFormat == null) {
            // lookup type and create the data format from it
            DataFormatDefinition type = lookup(context, ref, DataFormatDefinition.class);
            if (type == null && context.getDataFormats() != null) {
                type = context.getDataFormats().get(ref);
            }
            if (type != null) {
                dataFormat = type.getDataFormat();
            }
        }
        return dataFormat;
    }

    private static <T> T lookup(CamelContext context, String ref, Class<T> type) {
        try {
            return context.getRegistry().lookup(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
    }

}
