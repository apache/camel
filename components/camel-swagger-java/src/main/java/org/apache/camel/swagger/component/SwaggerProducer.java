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
package org.apache.camel.swagger.component;

import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.Parameter;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwaggerProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SwaggerProducer.class);

    // TODO: delegate to actual producer

    private Swagger swagger;

    public SwaggerProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SwaggerEndpoint getEndpoint() {
        return (SwaggerEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String verb = getEndpoint().getVerb();
        String path = getEndpoint().getPath();

        Operation op = getSwaggerOperation(verb, path);
        if (op == null) {
            exchange.setException(new IllegalArgumentException("Swagger schema does not contain operation for " + verb + ":" + path));
            callback.done(true);
            return true;
        }

        try {
            // build context path to use for actual HTTP call
            // replace path parameters with value from header
            String contextPath = path;
            Map<String, Object> query = new LinkedHashMap<>();
            for (Parameter param : op.getParameters()) {
                if ("path".equals(param.getIn())) {
                    String name = param.getName();
                    if (name != null) {
                        String value = exchange.getIn().getHeader(name, String.class);
                        if (value != null) {
                            String key = "{" + name + "}";
                            contextPath = StringHelper.replaceAll(contextPath, key, value);
                        }
                    }
                } else if ("query".equals(param.getIn())) {
                    String name = param.getName();
                    if (name != null) {
                        String value = exchange.getIn().getHeader(name, String.class);
                        if (value != null) {
                            query.put(name, value);
                        }
                    }
                }
            }
            if (!query.isEmpty()) {
                String options = URISupport.createQueryString(query);
                contextPath = contextPath + "?" + options;
            }

            LOG.debug("Using context-path: {}", contextPath);

        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // TODO: bind to consumes context-type
        // TODO: if binding is turned on/off/auto etc
        // TODO: use the component and build uri with verb/path
        // TODO: build dynamic uri for component (toD, headers)

        exchange.getIn().setBody("Hello Donald Duck");

        // do some binding first
        callback.done(true);
        return true;
    }

    private Operation getSwaggerOperation(String verb, String path) {
        Path modelPath = swagger.getPath(path);
        if (modelPath == null) {
            return null;
        }

        // get,put,post,head,delete,patch,options
        Operation op = null;
        if ("get".equals(verb)) {
            op = modelPath.getGet();
        } else if ("put".equals(verb)) {
            op = modelPath.getPut();
        } else if ("post".equals(verb)) {
            op = modelPath.getPost();
        } else if ("head".equals(verb)) {
            op = modelPath.getHead();
        } else if ("delete".equals(verb)) {
            op = modelPath.getDelete();
        } else if ("patch".equals(verb)) {
            op = modelPath.getPatch();
        } else if ("options".equals(verb)) {
            op = modelPath.getOptions();
        }
        return op;
    }

    public Swagger getSwagger() {
        return swagger;
    }

    public void setSwagger(Swagger swagger) {
        this.swagger = swagger;
    }
}
