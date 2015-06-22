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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Validation helper for validating components, data formats and languages
 */
public final class ValidateHelper {

    private ValidateHelper() {
    }

    /**
     * Validates the component json file
     *
     * @param file        the json file
     * @param errorDetail details to add errors
     */
    public static void validate(File file, ErrorDetail errorDetail) {
        try {
            String json = loadText(new FileInputStream(file));

            boolean isComponent = json.contains("\"kind\": \"component\"");
            boolean isDataFormat = json.contains("\"kind\": \"dataformat\"");
            boolean isLanguage = json.contains("\"kind\": \"language\"");

            // only check these kind
            if (!isComponent && !isDataFormat && !isLanguage) {
                return;
            }

            if (isComponent) {
                errorDetail.setKind("component");
            } else if (isDataFormat) {
                errorDetail.setKind("dataformat");
            } else if (isLanguage) {
                errorDetail.setKind("language");
            }

            List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema(errorDetail.getKind(), json, false);
            boolean label = false;
            boolean description = false;
            boolean syntax = false;
            for (Map<String, String> row : rows) {
                String value = row.get("label");
                if (!StringHelper.isEmpty(value)) {
                    label = true;
                }
                value = row.get("description");
                if (!StringHelper.isEmpty(value)) {
                    description = true;
                }
                value = row.get("syntax");
                if (!StringHelper.isEmpty(value)) {
                    syntax = true;
                }
            }
            
            if (!label) {
                errorDetail.setMissingLabel(true);
            }
            
            if (!description) {
                errorDetail.setMissingDescription(true);
            }

            // syntax check is only for the components
            if (!syntax && isComponent) {
                errorDetail.setMissingSyntax(true);
            }

            if (isComponent) {
                // check all the component properties if they have description
                rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
                for (Map<String, String> row : rows) {
                    String key = row.get("name");
                    String doc = row.get("description");
                    if (doc == null || doc.isEmpty()) {
                        errorDetail.addMissingComponentDoc(key);
                    }
                }
            }

            // check all the endpoint properties if they have description
            rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
            boolean path = false;
            for (Map<String, String> row : rows) {
                String key = row.get("name");
                String doc = row.get("description");
                if (doc == null || doc.isEmpty()) {
                    errorDetail.addMissingEndpointDoc(key);
                }
                String kind = row.get("kind");
                if ("path".equals(kind)) {
                    path = true;
                }
            }
            if (isComponent && !path) {
                // only components can have missing @UriPath
                errorDetail.setMissingUriPath(true);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Returns the name of the component, data format or language from the given json file
     */
    public static String asName(File file) {
        String name = file.getName();
        if (name.endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

}
