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
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwaggerProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SwaggerProducer.class);

    private Swagger swagger;
    private Operation operation;
    private AsyncProcessor producer;

    public SwaggerProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SwaggerEndpoint getEndpoint() {
        return (SwaggerEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // TODO: bind to consumes context-type
        // TODO: if binding is turned on/off/auto etc

        try {
            if (producer != null) {
                prepareExchange(exchange);
                return producer.process(exchange, callback);
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }

        // some error or there was no producer, so we are done
        callback.done(true);
        return true;
    }

    protected void prepareExchange(Exchange exchange) throws Exception {
        boolean hasPath = false;
        boolean hasQuery = false;

        // uri template with path parameters resolved
        String resolvedUriTemplate = getEndpoint().getPath();
        // for query parameters
        Map<String, Object> query = new LinkedHashMap<>();
        for (Parameter param : operation.getParameters()) {
            if ("query".equals(param.getIn())) {
                String name = param.getName();
                if (name != null) {
                    String value = exchange.getIn().getHeader(name, String.class);
                    if (value != null) {
                        hasQuery = true;
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
                    hasPath = true;
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

        if (hasQuery) {
            String queryParameters = URISupport.createQueryString(query);
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, queryParameters);
        }

        if (hasPath) {
            String scheme = swagger.getSchemes() != null && swagger.getSchemes().size() == 1 ? swagger.getSchemes().get(0).toValue() : "http";
            String host = getEndpoint().getHost() != null ? getEndpoint().getHost() : swagger.getHost();
            String basePath = swagger.getBasePath();
            basePath = FileUtil.stripLeadingSeparator(basePath);
            resolvedUriTemplate = FileUtil.stripLeadingSeparator(resolvedUriTemplate);
            // if so us a header for the dynamic uri template so we reuse same endpoint but the header overrides the actual url to use
            String overrideUri = String.format("%s://%s/%s/%s", scheme, host, basePath, resolvedUriTemplate);
            exchange.getIn().setHeader(Exchange.HTTP_URI, overrideUri);
        }
    }

    public Swagger getSwagger() {
        return swagger;
    }

    public void setSwagger(Swagger swagger) {
        this.swagger = swagger;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String verb = getEndpoint().getVerb();
        String path = getEndpoint().getPath();

        operation = getSwaggerOperation(verb, path);
        if (operation == null) {
            throw new IllegalArgumentException("Swagger schema does not contain operation for " + verb + ":" + path);
        }

        Producer processor = createHttpProducer(operation, verb, path);
        if (processor != null) {
            producer = AsyncProcessorConverterHelper.convert(processor);
        }
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(producer);
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

    private Producer createHttpProducer(Operation operation, String verb, String path) throws Exception {

        LOG.debug("Using Swagger operation: {} with {} {}", operation, verb, path);

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
            LOG.debug("Using RestProducerFactory: {}", factory);

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

            return factory.createProducer(getEndpoint().getCamelContext(), scheme, host, verb, basePath, uriTemplate,
                    (consumes.isEmpty() ? "" : consumes.toString()), (produces.isEmpty() ? "" : produces.toString()), null);

        } else {
            throw new IllegalStateException("Cannot find RestProducerFactory in Registry or as a Component to use");
        }
    }
}
