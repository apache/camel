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
package org.apache.camel.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.camel.util.FileUtil;

public final class OpenApiHelper {

    private OpenApiHelper() {
    }

    public static String buildUrl(String path1, String path2) {
        String answer;
        String s1 = FileUtil.stripTrailingSeparator(path1);
        String s2 = FileUtil.stripLeadingSeparator(path2);
        if (s1 != null && s2 != null) {
            answer = s1 + "/" + s2;
        } else if (path1 != null) {
            answer = path1;
        } else {
            answer = path2;
        }
        // must start with leading slash
        if (answer != null && !answer.startsWith("/")) {
            answer = "/" + answer;
        }
        return answer;
    }

    /**
     * Clears all the vendor extension on the openApi model. This may be needed as some API tooling does not support
     * this.
     */
    public static void clearVendorExtensions(OpenAPI openApi) {
        if (openApi.getExtensions() != null) {
            openApi.getExtensions().clear();
        }
        if (openApi.getComponents() != null
                && openApi.getComponents().getSchemas() != null) {
            for (Schema<?> schemaDefinition : openApi.getComponents().getSchemas().values()) {
                schemaDefinition.getExtensions().clear();
            }
        }
        if (openApi.getPaths() != null) {
            for (PathItem path : openApi.getPaths().values()) {
                if (path.getExtensions() != null) {
                    path.getExtensions().clear();
                }
                for (Operation op : path.readOperationsMap().values()) {
                    if (op.getExtensions() != null) {
                        op.getExtensions().clear();
                    }
                }
            }
        }
    }
}
