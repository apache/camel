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

/**
 * A type converter loader, that <b>only</b> supports scanning a {@link org.apache.camel.TypeConverters} class for
 * methods that has been annotated with {@link org.apache.camel.Converter}.
 */
public class TypeConvertersLoader extends AnnotationTypeConverterLoader {

    private final String name;

    /**
     * Creates the loader
     *
     * @param typeConverters The implementation that has the type converters
     */
    public TypeConvertersLoader(Object typeConverters) {
        this(typeConverters.getClass());
    }

    /**
     * Creates the loader
     *
     * @param clazz the class with the @Converter annotation and converter methods
     */
    public TypeConvertersLoader(Class<?> clazz) {
        super(new TypeConvertersPackageScanClassResolver(clazz));
        this.name = clazz.getPackage().getName();
    }

    @Override
    protected String[] findPackageNames() throws IOException {
        // this method doesn't change the behavior of the CorePackageScanClassResolver
        return new String[] { name };
    }

}
