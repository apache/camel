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
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.FactoryFinder;

/**
 * Default data format resolver
 *
 * @version 
 */
public class DefaultDataFormatResolver implements DataFormatResolver {

    public static final String DATAFORMAT_RESOURCE_PATH = "META-INF/services/org/apache/camel/dataformat/";

    protected FactoryFinder dataformatFactory;

    public DataFormat resolveDataFormat(String name, CamelContext context) {
        DataFormat dataFormat = lookup(context, name, DataFormat.class);
        if (dataFormat == null) {
            Class<?> type = null;
            try {
                if (dataformatFactory == null) {
                    dataformatFactory = context.getFactoryFinder(DATAFORMAT_RESOURCE_PATH);
                }
                type = dataformatFactory.findClass(name);
            } catch (NoFactoryAvailableException e) {
                // ignore
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid URI, no DataFormat registered for scheme: " + name, e);
            }

            if (type == null) {
                type = context.getClassResolver().resolveClass(name);
            }

            if (type != null) {
                if (DataFormat.class.isAssignableFrom(type)) {
                    dataFormat = (DataFormat) context.getInjector().newInstance(type);
                } else {
                    throw new IllegalArgumentException("Resolving dataformat: " + name + " detected type conflict: Not a DataFormat implementation. Found: " + type.getName());
                }
            }
        }

        return dataFormat;
    }

    private static <T> T lookup(CamelContext context, String ref, Class<T> type) {
        try {
            return context.getRegistry().lookupByNameAndType(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
    }
}
