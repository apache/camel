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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Validation helper for validating components, data formats and languages
 */
public final class ValidateHelper {

    private ValidateHelper() {
    }

    /**
     * Validates the component json file
     *
     * @param file the json file
     * @param errorDetail details to add errors
     */
    public static void validate(File file, ErrorDetail errorDetail) {
        try {
            String json = PackageHelper.loadText(file);
            JsonObject obj = (JsonObject)Jsoner.deserialize(json);

            Map<String, Object> model;
            boolean isComponent = (model = obj.getMap("component")) != null;
            boolean isDataFormat = !isComponent && (model = obj.getMap("dataformat")) != null;
            boolean isLanguage = !isComponent && !isDataFormat && (model = obj.getMap("language")) != null;

            // only check these kind
            if (!isComponent && !isDataFormat && !isLanguage) {
                return;
            }

            errorDetail.setKind((String)model.get("kind"));
            errorDetail.setMissingDescription(isNullOrEmpty(model.get("description")));
            errorDetail.setMissingLabel(isNullOrEmpty(model.get("label")));
            if (isComponent) {
                errorDetail.setMissingSyntax(isNullOrEmpty(model.get("syntax")));
                Map<String, Object> componentProps = obj.getMap("componentProperties");
                for (Map.Entry<String, Object> entry : componentProps.entrySet()) {
                    if (isNullOrEmpty(((JsonObject)entry.getValue()).get("description"))) {
                        errorDetail.addMissingComponentDoc(entry.getKey());
                    }
                }
            }
            Map<String, Object> props = obj.getMap("properties");
            boolean path = false;
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                JsonObject value = (JsonObject)entry.getValue();
                if (isNullOrEmpty(value.get("description"))) {
                    errorDetail.addMissingEndpointDoc(entry.getKey());
                }
                path |= "path".equals(value.get("kind"));
            }
            errorDetail.setMissingUriPath(isComponent && !path);
        } catch (DeserializationException e) {
            // wrap parsing exceptions as runtime
            throw new RuntimeException("Cannot parse json", e);
        } catch (IOException e) {
            // ignore
        }
    }

    private static boolean isNullOrEmpty(Object obj) {
        return obj == null || "".equals(obj);
    }

}
