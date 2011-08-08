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
package org.apache.camel.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.camel.spi.ClassResolver;

/**
 * Helper for loading resources on the classpath or file system.
 */
public final class ResourceHelper {

    private ResourceHelper() {
        // utility class
    }

    /**
     * Resolves the mandatory resource.
     * <p/>
     * If possible prefer to use {@link #resolveMandatoryResourceAsUrl(org.apache.camel.spi.ClassResolver, String)}
     * if possible.
     *
     * @param classResolver the class resolver to load the resource from the classpath
     * @param uri uri of the resource
     * @return the resource as an {@link InputStream}, remember to close the stream after usage.
     * @throws java.io.IOException is thrown if the resource file could not be found or loaded as {@link InputStream}
     */
    public static InputStream resolveMandatoryResourceAsInputStream(ClassResolver classResolver, String uri) throws IOException {
        if (uri.startsWith("file:")) {
            uri = ObjectHelper.after(uri, "file:");
            return new FileInputStream(uri);
        } else if (uri.startsWith("http:")) {
            URL url = new URL(uri);
            URLConnection con = url.openConnection();
            con.setUseCaches(false);
            try {
                return con.getInputStream();
            } catch (IOException e) {
                // close the http connection to avoid
                // leaking gaps in case of an exception
                if (con instanceof HttpURLConnection) {
                    ((HttpURLConnection) con).disconnect();
                }
                throw e;
            }
        } else if (uri.startsWith("classpath:")) {
            uri = ObjectHelper.after(uri, "classpath:");
        }

        // load from classpath by default
        InputStream is = classResolver.loadResourceAsStream(uri);
        if (is == null) {
            throw new FileNotFoundException("Cannot find resource in classpath for URI: " + uri);
        } else {
            return is;
        }
    }

    /**
     * Resolves the mandatory resource.
     *
     * @param classResolver the class resolver to load the resource from the classpath
     * @param uri uri of the resource
     * @return the resource as an {@link InputStream}, remember to close the stream after usage.
     * @throws java.io.FileNotFoundException is thrown if the resource file could not be found
     * @throws java.net.MalformedURLException if the uri is malformed
     */
    public static URL resolveMandatoryResourceAsUrl(ClassResolver classResolver,  String uri) throws FileNotFoundException, MalformedURLException {
        if (uri.startsWith("file:")) {
            return new URL(uri);
        } else if (uri.startsWith("http:")) {
            return new URL(uri);
        } else if (uri.startsWith("classpath:")) {
            uri = ObjectHelper.after(uri, "classpath:");
        }

        // load from classpath by default
        URL url = classResolver.loadResourceAsURL(uri);
        if (url == null) {
            throw new FileNotFoundException("Cannot find resource in classpath for URI: " + uri);
        } else {
            return url;
        }
    }

}
