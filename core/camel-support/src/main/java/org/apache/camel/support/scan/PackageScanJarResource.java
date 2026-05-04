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
package org.apache.camel.support.scan;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.camel.support.ResourceSupport;

public class PackageScanJarResource extends ResourceSupport {

    private final URL url;

    public PackageScanJarResource(String scheme, URL url, String shortName) {
        super(scheme, url.getFile() + shortName);
        this.url = url;
    }

    @Override
    public URL getURL() throws MalformedURLException {
        return url;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public URI getURI() {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            URL jarUrl = URI.create("jar:" + getLocation()).toURL();
            URLConnection con = jarUrl.openConnection();
            con.setUseCaches(false);
            return con.getInputStream();
        } catch (Exception e) {
            throw new IOException("Cannot open JAR resource: " + getLocation(), e);
        }
    }

}
