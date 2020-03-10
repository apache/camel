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
package org.apache.camel.generator.openapi;

import java.util.LinkedHashMap;
import java.util.Map;

import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import org.apache.camel.util.ObjectHelper;

class PathVisitor<T> {

    private final DestinationGenerator destinationGenerator;

    private final CodeEmitter<T> emitter;

    private final OperationFilter filter;

    public enum HttpMethod {
        DELETE,
        GET,
        HEAD,
        OPTIONS,
        PATCH,
        POST,
        PUT
    }

    PathVisitor(final String basePath, final CodeEmitter<T> emitter, final OperationFilter filter, final DestinationGenerator destinationGenerator) {
        this.emitter = emitter;
        this.filter = filter;
        this.destinationGenerator = destinationGenerator;

        if (ObjectHelper.isEmpty(basePath)) {
            emitter.emit("rest");
        } else {
            emitter.emit("rest", basePath);
        }
    }

    void visit(final OasPathItem definition) {
        final OperationVisitor<T> restDslOperation = new OperationVisitor<>(emitter, filter, definition.getPath(), destinationGenerator);

        operationMapFrom(definition).forEach(restDslOperation::visit);
    }

    void visit(final String path, final OasPathItem definition) {
        final OperationVisitor<T> restDslOperation = new OperationVisitor<>(emitter, filter, path, destinationGenerator);

        operationMapFrom(definition).forEach(restDslOperation::visit);
    }

    private static Map<HttpMethod, OasOperation> operationMapFrom(final OasPathItem path) {
        final Map<HttpMethod, OasOperation> result = new LinkedHashMap<>();

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

}
