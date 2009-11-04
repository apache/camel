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

/**
 * Default data format resolver
 *
 * @version $Revision$
 */
public class DefaultDataFormatResolver implements DataFormatResolver {

    @SuppressWarnings("unchecked")
    public DataFormat resolveDataFormat(DataFormatDefinition definition, CamelContext context) {
        Class type = context.getClassResolver().resolveClass(definition.getDataFormatName());
        if (type == null) {
            throw new IllegalArgumentException("The class " + definition.getDataFormatName()
                + " is not on the classpath! Cannot use the dataFormat " + this);
        }
        return (DataFormat) context.getInjector().newInstance(type);
    }

    public DataFormat resolveDataFormat(String ref, CamelContext context) {
        DataFormat dataFormat = context.getRegistry().lookup(ref, DataFormat.class);
        if (dataFormat == null) {
            // lookup type and create the data format from it
            DataFormatDefinition type = context.getRegistry().lookup(ref, DataFormatDefinition.class);
            if (type == null && context.getDataFormats() != null) {
                type = context.getDataFormats().get(ref);
            }
            if (type != null) {
                dataFormat = resolveDataFormat(type, context);
            }
        }
        if (dataFormat == null) {
            throw new IllegalArgumentException("Cannot find data format in registry with ref: " + ref);
        }
        return dataFormat;
    }

}
