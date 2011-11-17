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
package org.apache.camel.component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.camel.spi.ClassResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;

/**
 * Camel specific {@link ClassPathResource} which uses the {@link ClassResolver} to load resources from the classpath.
 */
public class CamelClassPathResource extends ClassPathResource {

    private final ClassResolver resolver;

    public CamelClassPathResource(ClassResolver resolver, String path, ClassLoader classLoader) {
        super(path, classLoader);
        Assert.notNull(resolver, "Resolver must not be null");
        this.resolver = resolver;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is = resolver.loadResourceAsStream(getPath());
        if (is == null) {
            return super.getInputStream();
        } else {
            return is;
        }
    }

    @Override
    public URL getURL() throws IOException {
        URL url = resolver.loadResourceAsURL(getPath());
        if (url == null) {
            return super.getURL();
        } else {
            return url;
        }
    }
}
