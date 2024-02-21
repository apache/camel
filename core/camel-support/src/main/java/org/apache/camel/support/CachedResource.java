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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.apache.camel.spi.Resource;

/**
 * A resource which will cache the content of the input stream on first read.
 */
public class CachedResource extends ResourceSupport {

    private final Resource delegate;
    private byte[] data;

    public CachedResource(Resource delegate) {
        super("cached:" + delegate.getScheme(), delegate.getLocation());
        this.delegate = delegate;
    }

    @Override
    public boolean exists() {
        return delegate.exists();
    }

    @Override
    public URI getURI() {
        return delegate.getURI();
    }

    @Override
    public URL getURL() throws MalformedURLException {
        return delegate.getURL();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (data == null) {
            try (InputStream is = delegate.getInputStream()) {
                data = is.readAllBytes();
            }
        }
        return new ByteArrayInputStream(data);
    }
}
