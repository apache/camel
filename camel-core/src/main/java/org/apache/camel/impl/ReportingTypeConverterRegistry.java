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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.TypeConverter;
import org.apache.camel.impl.converter.TypeConverterRegistry;
import org.apache.camel.spi.Injector;

/**
 * Registry for reporting type converters.
 * <p/>
 * Used by the camel-maven-plugin.
 */
public class ReportingTypeConverterRegistry implements TypeConverterRegistry {
    private List<String> errors = new ArrayList<String>();

    public String[] getErrors() {
        return errors.toArray(new String[errors.size()]);
    }

    public void addTypeConverter(Class toType, Class fromType, TypeConverter typeConverter) {
        if (errors.size() == 0) {
            errors.add("Method should not be invoked.");
        }
    }

    public Injector getInjector() {
        return null;
    }

}
