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
package org.apache.camel.impl.converter;

import java.io.IOException;

import org.apache.camel.TypeConverters;

/**
 * A type converter loader, that <b>only</b> supports scanning a {@link org.apache.camel.TypeConverters} class
 * for methods that has been annotated with {@link org.apache.camel.Converter}.
 */
public class TypeConvertersLoader extends AnnotationTypeConverterLoader {

    private final TypeConverters typeConverters;

    /**
     * Creates the loader
     *
     * @param typeConverters  The implementation that has the type converters
     */
    public TypeConvertersLoader(TypeConverters typeConverters) {
        super(new TypeConvertersPackageScanClassResolver(typeConverters.getClass()));
        this.typeConverters = typeConverters;
    }

    @Override
    protected String[] findPackageNames() throws IOException {
        // this method doesn't change the behavior of the CorePackageScanClassResolver
        String name = typeConverters.getClass().getPackage().getName();
        return new String[]{name};
    }

}
