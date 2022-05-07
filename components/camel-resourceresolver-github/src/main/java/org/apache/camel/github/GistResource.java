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
package org.apache.camel.github;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceSupport;

public final class GistResource extends ResourceSupport {

    private final CamelContext camelContext;
    private byte[] cache;
    private boolean init;

    public GistResource(CamelContext camelContext, String location) {
        super("gist", location);
        this.camelContext = camelContext;
    }

    @Override
    public boolean exists() {
        if (!init) {
            try {
                URL u = new URL(getLocation());
                try (InputStream is = u.openStream()) {
                    cache = camelContext.getTypeConverter().tryConvertTo(byte[].class, is);
                }
            } catch (Exception e) {
                // ignore
            }
            init = true;
        }
        return cache != null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (exists()) {
            return new ByteArrayInputStream(cache);
        } else {
            return null;
        }
    }
}
