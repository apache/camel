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
package org.apache.camel.component.olingo2.api.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.olingo.odata2.api.uri.PathSegment;

/**
 * Copied from Olingo2 library, since URI parsing wasn't made a part of it's
 * public API.
 */
public class ODataPathSegmentImpl implements PathSegment {

    private String path;
    private Map<String, List<String>> matrixParameter;

    public ODataPathSegmentImpl(final String path, final Map<String, List<String>> matrixParameters) {
        this.path = path;

        Map<String, List<String>> unmodifiableMap = new HashMap<>();
        if (matrixParameters != null) {
            for (String key : matrixParameters.keySet()) {
                List<String> values = Collections.unmodifiableList(matrixParameters.get(key));
                unmodifiableMap.put(key, values);
            }
        }

        matrixParameter = Collections.unmodifiableMap(unmodifiableMap);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Map<String, List<String>> getMatrixParameters() {
        return matrixParameter;
    }

}
