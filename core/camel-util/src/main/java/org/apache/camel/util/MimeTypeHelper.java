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
package org.apache.camel.util;

import java.util.StringJoiner;

public class MimeTypeHelper {

    /**
     * Sanitizes the mime types after URL encoding to convert space into plus sign.
     *
     * @param  types mime types such as from HTTP Accept header
     * @return       the sanitized mime types
     */
    public static String sanitizeMimeType(String types) {
        if (types != null) {
            StringJoiner sj = new StringJoiner(",");
            for (String part : types.split(",")) {
                sj.add(part.trim().replace(' ', '+'));
            }
            types = sj.toString();
        }
        return types;
    }

}
