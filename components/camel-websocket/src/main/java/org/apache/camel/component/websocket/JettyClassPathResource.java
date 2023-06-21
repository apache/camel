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
package org.apache.camel.component.websocket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.util.resource.Resource;

/**
 * A Jetty {@link Resource} to load from the classpath using Camels {@link ClassResolver} which ensures loading
 * resources works in OSGi and other containers.
 */
public class JettyClassPathResource extends Resource {

    private final ClassResolver resolver;
    private final String path;

    public JettyClassPathResource(ClassResolver resolver, String path) {
        ObjectHelper.notNull(resolver, "ClassResolver");
        ObjectHelper.notNull(path, "path");
        this.resolver = resolver;
        this.path = path;
    }

    @Override
    public boolean isContainedIn(Resource r) throws MalformedURLException {
        return false;
    }

    @Override
    public boolean exists() {
        InputStream is = resolver.loadResourceAsStream(path);
        if (is != null) {
            IOHelper.close(is);
        }
        return is != null;
    }

    @Override
    public boolean isDirectory() {
        return exists() && path.endsWith("/");
    }

    @Override
    public long lastModified() {
        return 0;
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public URI getURI() {
        URL url = getURL();
        if (url == null) {
            return null;
        }
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public URL getURL() {
        return resolver.loadResourceAsURL(path);
    }

    @Override
    public File getFile() throws IOException {
        URL url = getURL();
        if (url != null) {
            return new File(url.getFile());
        }
        return null;
    }

    @Override
    public String getName() {
        return path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return resolver.loadResourceAsStream(path);
    }

    @Override
    public boolean delete() throws SecurityException {
        return false;
    }

    @Override
    public boolean renameTo(Resource dest) throws SecurityException {
        return false;
    }

    @Override
    public String[] list() {
        return new String[0];
    }

    @Override
    public Resource addPath(String path) throws IOException {
        return new JettyClassPathResource(resolver, this.path + "/" + path);
    }

    @Override
    public void close() {
        // noop
    }

    @Override
    public ReadableByteChannel getReadableByteChannel() throws IOException {
        return null;
    }
}
