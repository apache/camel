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
package org.apache.camel.component.snakeyaml.custom;

import java.util.Objects;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * A CustomClassLoaderConstructor which allows to set the LoaderOptions
 */
public class CustomClassLoaderConstructor extends Constructor {

    private final ClassLoader loader;

    public CustomClassLoaderConstructor(ClassLoader theLoader, LoaderOptions options) {
        super(Object.class, options);
        this.loader = Objects.requireNonNull(theLoader, "Loader must be provided.");
    }

    @Override
    protected Class<?> getClassForName(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }
}
