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
package org.apache.camel.component.velocity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.util.ExtProperties;

/**
 * Camel specific {@link ClasspathResourceLoader} that loads resources using the
 * Camel {@link ClassResolver} used by the Velocity.
 */
public class CamelVelocityClasspathResourceLoader extends ClasspathResourceLoader {

    private ClassResolver resolver;

    @Override
    public void init(ExtProperties configuration) {
        super.init(configuration);
        resolver = (ClassResolver) this.rsvc.getProperty("CamelClassResolver");
        ObjectHelper.notNull(resolver, "ClassResolver");
    }

    @Override
    public Reader getResourceReader(String name, String encoding) throws ResourceNotFoundException {
        InputStream is = resolver.loadResourceAsStream(name);
        if (is == null) {
            return super.getResourceReader(name, encoding);
        } else {
            return new InputStreamReader(is);
        }
    }

}