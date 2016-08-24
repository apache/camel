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
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.Parameter;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwaggerProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SwaggerProducer.class);

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

        Operation operation = getSwaggerOperation(verb, path);
        if (operation == null) {
            exchange.setException(new IllegalArgumentException("Swagger schema does not contain operation for " + verb + ":" + path));
            callback.done(true);
            return true;
        }

        try {
            // TODO: bind to consumes context-type
            // TODO: if binding is turned on/off/auto etc
            // TODO: build dynamic uri for component (toD, headers)
            // create http producer to use for calling the remote HTTP service
            // TODO: create the producer once and reuse (create HTTP_XXX headers for dynamic values)
            Producer producer = createHttpProducer(exchange, operation, verb, path);
            if (producer != null) {
                ServiceHelper.startService(producer);
                AsyncProcessor async = AsyncProcessorConverterHelper.convert(producer);
                return async.process(exchange, callback);
            }

        } catch (Throwable e) {
            exchange.setException(e);
        }

        // some error or there was no producer, so we are done
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

    protected Producer createHttpProducer(Exchange exchange, Operation operation, String verb, String path) throws Exception {
        RestProducerFactory factory = null;
        String cname = null;
        if (getEndpoint().getComponentName() != null) {
            Object comp = getEndpoint().getCamelContext().getRegistry().lookupByName(getEndpoint().getComponentName());
            if (comp != null && comp instanceof RestProducerFactory) {
                factory = (RestProducerFactory) comp;
            } else {
                comp = getEndpoint().getCamelContext().getComponent(getEndpoint().getComponentName());
                if (comp != null && comp instanceof RestProducerFactory) {
                    factory = (RestProducerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + getEndpoint().getComponentName() + " is not a RestProducerFactory");
                } else {
                    throw new NoSuchBeanException(getEndpoint().getComponentName(), RestProducerFactory.class.getName());
                }
            }
            cname = getEndpoint().getComponentName();
        }

        // try all components
        if (factory == null) {
            for (String name : getEndpoint().getCamelContext().getComponentNames()) {
                Component comp = getEndpoint().getCamelContext().getComponent(name);
                if (comp != null && comp instanceof RestProducerFactory) {
                    factory = (RestProducerFactory) comp;
                    cname = name;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestProducerFactory> factories = getEndpoint().getCamelContext().getRegistry().findByType(RestProducerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        if (factory != null) {

            CollectionStringBuffer produces = new CollectionStringBuffer(",");
            List<String> list = operation.getProduces();
            if (list == null) {
                list = swagger.getProduces();
            }
            if (list != null) {
                for (String s : list) {
                    produces.append(s);
                }
            }
            CollectionStringBuffer consumes = new CollectionStringBuffer(",");
            list = operation.getConsumes();
            if (list == null) {
                list = swagger.getConsumes();
            }
            if (list != null) {
                for (String s : list) {
                    consumes.append(s);
                }
            }

            // TODO: allow to chose scheme if there is multiple
            String scheme = swagger.getSchemes() != null && swagger.getSchemes().size() == 1 ? swagger.getSchemes().get(0).toValue() : "http";
            String host = getEndpoint().getHost() != null ? getEndpoint().getHost() : swagger.getHost();
            String basePath = swagger.getBasePath();
            String uriTemplate = path;

            // uri template with path parameters resolved
            String resolvedUriTemplate = uriTemplate;
            // for query parameters
            Map<String, Object> query = new LinkedHashMap<>();
            for (Parameter param : operation.getParameters()) {
                if ("query".equals(param.getIn())) {
                    String name = param.getName();
                    if (name != null) {
                        String value = exchange.getIn().getHeader(name, String.class);
                        if (value != null) {
                            // we need to remove the header as they are sent as query instead
                            // TODO: we could use a header filter strategy to skip these headers
                            exchange.getIn().removeHeader(param.getName());
                            query.put(name, value);
                        } else if (param.getRequired()) {
                            throw new NoSuchHeaderException(exchange, name, String.class);
                        }
                    }
                } else if ("path".equals(param.getIn())) {
                    String value = exchange.getIn().getHeader(param.getName(), String.class);
                    if (value != null) {
                        // we need to remove the header as they are sent as path instead
                        // TODO: we could use a header filter strategy to skip these headers
                        exchange.getIn().removeHeader(param.getName());
                        String token = "{" + param.getName() + "}";
                        resolvedUriTemplate = StringHelper.replaceAll(resolvedUriTemplate, token, value);
                    } else if (param.getRequired()) {
                        // the parameter is required but we do not have a header
                        throw new NoSuchHeaderException(exchange, param.getName(), String.class);
                    }
                }
            }
            // build as query string
            String queryParameters = null;
            if (!query.isEmpty()) {
                queryParameters = URISupport.createQueryString(query);
            }

            return factory.createProducer(getEndpoint().getCamelContext(), exchange, scheme, host, verb, basePath, uriTemplate, resolvedUriTemplate,
                    queryParameters, (consumes.isEmpty() ? "" : consumes.toString()), (produces.isEmpty() ? "" : produces.toString()), null);

        } else {
            throw new IllegalStateException("Cannot find RestProducerFactory in Registry or as a Component to use");
        }
    }
}
