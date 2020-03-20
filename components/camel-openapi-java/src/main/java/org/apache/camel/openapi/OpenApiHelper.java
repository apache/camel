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

import java.util.LinkedHashMap;
import java.util.Map;

import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30SchemaDefinition;
import org.apache.camel.util.FileUtil;

public final class OpenApiHelper {

    private OpenApiHelper() {
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

    /**
     * Clears all the vendor extension on the openApi model. This may be needed as some API tooling does not support this.
     */
    public static void clearVendorExtensions(OasDocument openApi) {
        
        if (openApi instanceof Oas20Document) {
        
            if (openApi.getExtensions() != null) {
                openApi.getExtensions().clear();
            }

            if (((Oas20Document)openApi).definitions.getDefinitions() != null) {
                for (Oas20SchemaDefinition schemaDefinition : ((Oas20Document)openApi).definitions.getDefinitions()) {
                    if (schemaDefinition.getExtensions() != null) {
                        schemaDefinition.getExtensions().clear();
                    }
                }
            }

            if (openApi.paths != null) {
                for (OasPathItem path : openApi.paths.getPathItems()) {
                    if (path.getExtensions() != null) {
                        path.getExtensions().clear();
                    }
                    for (OasOperation op : getOperationMap(path).values()) {
                        if (op.getExtensions() != null) {
                            op.getExtensions().clear();
                        }
                    }
                }
            }
        } else if (openApi instanceof Oas30Document) {
            if (openApi.getExtensions() != null) {
                openApi.getExtensions().clear();
            }

            if (((Oas30Document)openApi).components != null 
                && ((Oas30Document)openApi).components.schemas != null) {
                for (Oas30SchemaDefinition schemaDefinition : ((Oas30Document)openApi).components.schemas.values()) {
                    if (schemaDefinition.getExtensions() != null) {
                        schemaDefinition.getExtensions().clear();
                    }
                }
            }

            if (openApi.paths != null) {
                for (OasPathItem path : openApi.paths.getPathItems()) {
                    if (path.getExtensions() != null) {
                        path.getExtensions().clear();
                    }
                    for (OasOperation op : getOperationMap(path).values()) {
                        if (op.getExtensions() != null) {
                            op.getExtensions().clear();
                        }
                    }
                }
            }
        }

    }
    
    private static Map<HttpMethod, OasOperation> getOperationMap(OasPathItem path) {
        Map<HttpMethod, OasOperation> result = new LinkedHashMap<HttpMethod, OasOperation>();

        if (path.get != null) {
            result.put(HttpMethod.GET, path.get);
        }
        if (path.put != null) {
            result.put(HttpMethod.PUT, path.put);
        }
        if (path.post != null) {
            result.put(HttpMethod.POST, path.post);
        }
        if (path.delete != null) {
            result.put(HttpMethod.DELETE, path.delete);
        }
        if (path.patch != null) {
            result.put(HttpMethod.PATCH, path.patch);
        }
        if (path.head != null) {
            result.put(HttpMethod.HEAD, path.head);
        }
        if (path.options != null) {
            result.put(HttpMethod.OPTIONS, path.options);
        }

        return result;
    }
    
    enum HttpMethod {
        POST,
        GET,
        PUT,
        PATCH,
        DELETE,
        HEAD,
        OPTIONS
    }

}
