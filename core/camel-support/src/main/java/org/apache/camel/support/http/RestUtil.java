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
package org.apache.camel.support.http;

import java.util.Locale;

import org.apache.camel.util.StringHelper;

public final class RestUtil {

    /**
     * Used for validating incoming REST calls whether Camel can process according to consumes/produces and
     * Accept/Content-Type headers.
     */
    public static boolean isValidOrAcceptedContentType(String valid, String target) {
        if (valid == null || target == null) {
            return true;
        }

        // Any MIME type
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept#Directives
        if (target.contains("*/*")) {
            return true;
        }

        valid = valid.toLowerCase(Locale.ENGLISH);
        target = target.toLowerCase(Locale.ENGLISH);

        // try each part of the target (such as an accept header with more media types)
        for (String part : target.split(",")) {
            // the media type is before its optional parameters (such as charset or q)
            part = StringHelper.before(part, ";", part).trim();
            if (!part.isEmpty() && (valid.contains(part) || isAcceptedByMediaRange(valid, part))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the part is a media range (such as application/*) that accepts one of the valid media types, which is
     * any media type of the same type.
     */
    private static boolean isAcceptedByMediaRange(String valid, String part) {
        if (!part.endsWith("/*")) {
            return false;
        }
        String type = part.substring(0, part.length() - 2);
        if (type.isEmpty()) {
            return false;
        }
        for (String mediaType : valid.split(",")) {
            if (type.equals(StringHelper.before(mediaType.trim(), "/"))) {
                return true;
            }
        }
        return false;
    }

}
