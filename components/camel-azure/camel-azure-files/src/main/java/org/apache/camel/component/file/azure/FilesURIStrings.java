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
package org.apache.camel.component.file.azure;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Helper for Camel endpoint URI strings.
 * <p>
 * In Camel, URIs are most commonly passed as strings and they are flexible e.g. they could contain expressions that
 * prior evaluation could violate URI RFC, ...
 */
final class FilesURIStrings {

    public static final char QUERY_SEPARATOR = '?';

    private FilesURIStrings() {
    }

    /**
     * Get the base uri part before the options as they can be non URI valid such as the expression using $ chars and
     * the URI constructor will regard $ as an illegal character and we don't want to enforce end users to to escape the
     * $ for the expression (file language)
     */
    static URI getBaseURI(String uri) throws URISyntaxException {
        String baseUri = StringHelper.before(uri, QUERY_SEPARATOR, uri);
        return new URI(baseUri);
    }

    static String reconstructBase64EncodedValue(String value) {
        // base64 allows + and =, URI encoded as %2B and %3D
        // Camel URI configurers decode both + and %2B to a space
        return value.replace(" ", "+");
    }

    /**
     * Uses encoding style expected by the files service: it preserves time separator ':' and encodes base64 plus '+',
     * slash '/' and padding '='.
     */
    static String encodeTokenValue(String value) throws URISyntaxException {
        return URISupport.createQueryString(Collections.singletonMap("x", value)).substring(2)
                .replace("+", "%2B") // sig is base64
                .replace("%3A", ":"); // se has time separator
    }

}
