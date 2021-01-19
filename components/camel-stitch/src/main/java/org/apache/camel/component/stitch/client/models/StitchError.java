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
package org.apache.camel.component.stitch.client.models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This represents the Error Object: https://www.stitchdata.com/docs/developers/import-api/api#error-object
 */
public class StitchError implements StitchModel {
    // property names
    public static final String ERROR = "error";
    public static final String ERRORS = "errors";

    private final String error;
    private final List<Object> errors;

    public StitchError(String error, List<Object> errors) {
        this.error = error;
        this.errors = errors;
    }

    public String getError() {
        return error;
    }

    public List<Object> getErrors() {
        return errors;
    }

    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> resultAsMap = new LinkedHashMap<>();

        resultAsMap.put(ERROR, error);
        resultAsMap.put(ERRORS, errors);

        return resultAsMap;
    }
}
