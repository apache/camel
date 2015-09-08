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
package org.apache.camel.component.atom;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.ParseException;
import org.apache.abdera.parser.Parser;
import org.apache.commons.codec.binary.Base64;

/**
 * Atom utilities.
 */
public final class AtomUtils {

    private AtomUtils() {
        // Helper class
    }

    /**
     * Gets the Atom parser.
     */
    public static Parser getAtomParser() {
        return Abdera.getInstance().getParser();
    }

    /**
     * Parses the given uri and returns the response as a atom feed document.
     *
     * @param uri the uri for the atom feed.
     * @return the document
     * @throws IOException    is thrown if error reading from the uri
     * @throws ParseException is thrown if the parsing failed
     */
    public static Document<Feed> parseDocument(String uri) throws IOException, ParseException {
        InputStream in = new URL(uri).openStream();
        return parseInputStream(in);
    }

    public static Document<Feed> parseDocument(String uri, String username, String password) throws IOException {
        URL feedUrl = new URL(uri);
        HttpURLConnection httpcon = (HttpURLConnection) feedUrl.openConnection();
        String encoding = Base64.encodeBase64String(username.concat(":").concat(password).getBytes());
        httpcon.setRequestProperty("Authorization", "Basic " + encoding);
        InputStream in = httpcon.getInputStream();
        return parseInputStream(in);
    }

    private static Document<Feed> parseInputStream(InputStream in) throws ParseException {
        Parser parser = getAtomParser();
        // set the thread context loader with the ParserClassLoader
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(parser.getClass().getClassLoader());
            return parser.parse(in);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

}
