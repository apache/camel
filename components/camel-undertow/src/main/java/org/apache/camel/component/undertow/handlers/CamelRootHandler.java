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
package org.apache.camel.component.undertow.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathTemplate;
import io.undertow.util.URLUtils;

/**
 * Custom root handler to enable hot swapping individual handlers assigned for each path template and/or HTTP method.
 */
public class CamelRootHandler implements HttpHandler {
    private CamelPathHandler pathHandler;

    public CamelRootHandler(HttpHandler defaultHandler) {
        pathHandler = new CamelPathHandler(defaultHandler);
    }

    public void handleRequest(HttpServerExchange exchange) throws Exception {
        pathHandler.handleRequest(exchange);
    }

    public synchronized HttpHandler add(String path, String methods, boolean prefixMatch, HttpHandler handler) {
        String basePath = getBasePath(path);
        HttpHandler basePathHandler = pathHandler.getHandler(basePath);

        CamelMethodHandler targetHandler;
        if (path.contains("{")) {
            // Adding a handler for the template path
            String relativePath = path.substring(basePath.length());
            if (basePathHandler instanceof CamelPathTemplateHandler) {
                CamelPathTemplateHandler templateHandler = (CamelPathTemplateHandler) basePathHandler;
                targetHandler = templateHandler.get(relativePath);
                if (targetHandler == null) {
                    targetHandler = new CamelMethodHandler();
                    templateHandler.add(relativePath, targetHandler);
                }
            } else {
                CamelPathTemplateHandler templateHandler;
                if (basePathHandler instanceof CamelMethodHandler) {
                    // A static path handler is already set for the base path. Use it as a default handler
                    templateHandler = new CamelPathTemplateHandler((CamelMethodHandler) basePathHandler);
                } else if (basePathHandler == null) {
                    templateHandler = new CamelPathTemplateHandler(new CamelMethodHandler());
                } else {
                    throw new IllegalArgumentException(String.format("Unsupported handler '%s' was found", basePathHandler));
                }
                targetHandler = new CamelMethodHandler();
                templateHandler.add(relativePath, targetHandler);
                pathHandler.addPrefixPath(basePath, templateHandler);
            }

        } else {
            // Adding a handler for the static path
            if (basePathHandler instanceof CamelPathTemplateHandler) {
                CamelPathTemplateHandler templateHandler = (CamelPathTemplateHandler) basePathHandler;
                if (!prefixMatch) {
                    targetHandler = templateHandler.getDefault();
                } else {
                    throw new IllegalArgumentException(String.format("Duplicate handlers on a path '%s'", path));
                }
            } else {
                if (basePathHandler instanceof CamelMethodHandler) {
                    targetHandler = (CamelMethodHandler) basePathHandler;
                } else if (basePathHandler == null) {
                    targetHandler = new CamelMethodHandler();
                    if (prefixMatch) {
                        pathHandler.addPrefixPath(basePath, targetHandler);
                    } else {
                        pathHandler.addExactPath(basePath, targetHandler);
                    }
                } else {
                    throw new IllegalArgumentException(String.format("Unsupported handler '%s' was found", basePathHandler));
                }
            }
        }
        return targetHandler.add(methods, handler);
    }

    public synchronized void remove(String path, String methods, boolean prefixMatch) {
        String basePath = getBasePath(path);
        HttpHandler basePathHandler = pathHandler.getHandler(basePath);
        if (basePathHandler == null) {
            return;
        }

        if (path.contains("{")) {
            // Removing a handler for the template path
            String relativePath = path.substring(basePath.length());
            CamelPathTemplateHandler templateHandler = (CamelPathTemplateHandler)basePathHandler;
            CamelMethodHandler targetHandler = templateHandler.get(relativePath);
            if (targetHandler.remove(methods)) {
                templateHandler.remove(relativePath);
                if (templateHandler.isEmpty()) {
                    pathHandler.removePrefixPath(basePath);
                }
            }

        } else {
            // Removing a handler for the static path
            if (basePathHandler instanceof CamelPathTemplateHandler) {
                String relativePath = path.substring(basePath.length());
                CamelPathTemplateHandler templateHandler = (CamelPathTemplateHandler)basePathHandler;
                CamelMethodHandler targetHandler = templateHandler.getDefault();
                if (targetHandler.remove(methods)) {
                    templateHandler.remove(relativePath);
                    if (templateHandler.isEmpty()) {
                        pathHandler.removePrefixPath(basePath);
                    }
                }
            } else {
                CamelMethodHandler targetHandler = (CamelMethodHandler)basePathHandler;
                if (targetHandler.remove(methods)) {
                    if (prefixMatch) {
                        pathHandler.removePrefixPath(basePath);
                    } else {
                        pathHandler.removeExactPath(basePath);
                    }
                }
            }
        }
    }

    public synchronized boolean isEmpty() {
        return pathHandler.isEmpty();
    }

    public String toString() {
        return pathHandler.toString();
    }

    private String getBasePath(String path) {
        if (path.contains("{")) {
            path = PathTemplate.create(path).getBase();
        }
        return URLUtils.normalizeSlashes(path);
    }
}
