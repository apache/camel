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
package org.apache.camel.swagger;

import org.apache.camel.util.FileUtil;

public final class SwaggerHelper {

    private SwaggerHelper() {
    }

    public static String buildUrl(String path1, String path2) {
        String s1 = FileUtil.stripTrailingSeparator(path1);
        String s2 = FileUtil.stripLeadingSeparator(path2);
        if (s1 != null && s2 != null) {
            return s1 + "/" + s2;
        } else if (path1 != null) {
            return path1;
        } else {
            return path2;
        }
    }
}
