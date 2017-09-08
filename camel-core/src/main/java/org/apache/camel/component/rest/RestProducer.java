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
package org.apache.camel.component.rest;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Rest producer for calling remote REST services.
 */
public class RestProducer extends DefaultAsyncProducer {

    private static final String ACCEPT = "Accept";
    private final CamelContext camelContext;
    private final RestConfiguration configuration;
    private boolean prepareUriTemplate = true;
    private RestBindingMode bindingMode;
    private Boolean skipBindingOnErrorCode;
    private String type;
    private String outType;

    // the producer of the Camel component that is used as the HTTP client to call the REST service
    private AsyncProcessor producer;
    // if binding is enabled then this processor should be used to wrap the call with binding before/after
    private AsyncProcessor binding;

    public RestProducer(Endpoint endpoint, Producer producer, RestConfiguration configuration) {
        super(endpoint);
        this.camelContext = endpoint.getCamelContext();
        this.configuration = configuration;
        this.producer = AsyncProcessorConverterHelper.convert(producer);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            prepareExchange(exchange);
            if (binding != null) {
                return binding.process(exchange, callback);
            } else {
                // no binding in use call the producer directly
                return producer.process(exchange, callback);
            }
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    @Override
    public RestEndpoint getEndpoint() {
        return (RestEndpoint) super.getEndpoint();
    }

    public boolean isPrepareUriTemplate() {
        return prepareUriTemplate;
    }

    /**
     * Whether to prepare the uri template and replace {key} with values from the exchange, and set
     * as {@link Exchange#HTTP_URI} header with the resolved uri to use instead of uri from endpoint.
     */
    public void setPrepareUriTemplate(boolean prepareUriTemplate) {
        this.prepareUriTemplate = prepareUriTemplate;
    }

    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(final RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public Boolean getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    public void setSkipBindingOnErrorCode(Boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String outType) {
        this.outType = outType;
    }

    protected void prepareExchange(Exchange exchange) throws Exception {
        boolean hasPath = false;

        // uri template with path parameters resolved
        // uri template may be optional and the user have entered the uri template in the path instead
        String resolvedUriTemplate = getEndpoint().getUriTemplate() != null ? getEndpoint().getUriTemplate() : getEndpoint().getPath();

        Message inMessage = exchange.getIn();
        if (prepareUriTemplate) {
            if (resolvedUriTemplate.contains("{")) {
                // resolve template and replace {key} with the values form the exchange
                // each {} is a parameter (url templating)
                String[] arr = resolvedUriTemplate.split("\\/");
                CollectionStringBuffer csb = new CollectionStringBuffer("/");
                for (String a : arr) {
                    if (a.startsWith("{") && a.endsWith("}")) {
                        String key = a.substring(1, a.length() - 1);
                        String value = inMessage.getHeader(key, String.class);
                        if (value != null) {
                            hasPath = true;
                            csb.append(value);
                        } else {
                            csb.append(a);
                        }
                    } else {
                        csb.append(a);
                    }
                }
                resolvedUriTemplate = csb.toString();
            }
        }

        // resolve uri parameters
        String query = createQueryParameters(getEndpoint().getQueryParameters(), inMessage);

        if (query != null) {
            // the query parameters for the rest call to be used
            inMessage.setHeader(Exchange.REST_HTTP_QUERY, query);
        }

        if (hasPath) {
            String host = getEndpoint().getHost();
            String basePath = getEndpoint().getUriTemplate() != null ? getEndpoint().getPath() :  null;
            basePath = FileUtil.stripLeadingSeparator(basePath);
            resolvedUriTemplate = FileUtil.stripLeadingSeparator(resolvedUriTemplate);
            // if so us a header for the dynamic uri template so we reuse same endpoint but the header overrides the actual url to use
            String overrideUri = host;
            if (!ObjectHelper.isEmpty(basePath)) {
                overrideUri += "/" + basePath;
            }
            if (!ObjectHelper.isEmpty(resolvedUriTemplate)) {
                overrideUri += "/" + resolvedUriTemplate;
            }
            // the http uri for the rest call to be used
            inMessage.setHeader(Exchange.REST_HTTP_URI, overrideUri);

            // when chaining RestConsumer with RestProducer, the
            // HTTP_PATH header will be present, we remove it here
            // as the REST_HTTP_URI contains the full URI for the
            // request and every other HTTP producer will concatenate
            // REST_HTTP_URI with HTTP_PATH resulting in incorrect URIs
            inMessage.removeHeader(Exchange.HTTP_PATH);
        }

        // method
        String method = getEndpoint().getMethod();
        if (method != null) {
            // the method should be in upper case 
            String upper = method.toUpperCase(Locale.US);
            inMessage.setHeader(Exchange.HTTP_METHOD, upper);
        }

        final String produces = getEndpoint().getProduces();
        if (isEmpty(inMessage.getHeader(Exchange.CONTENT_TYPE)) && isNotEmpty(produces)) {
            inMessage.setHeader(Exchange.CONTENT_TYPE, produces);
        }

        final String consumes = getEndpoint().getConsumes();
        if (isEmpty(inMessage.getHeader(ACCEPT)) && isNotEmpty(consumes)) {
            inMessage.setHeader(ACCEPT, consumes);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create binding processor (returns null if binding is not in use)
        binding = createBindingProcessor();

        ServiceHelper.startServices(binding, producer);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopServices(producer, binding);
    }

    protected AsyncProcessor createBindingProcessor() throws Exception {

        // these options can be overridden per endpoint
        String mode = configuration.getBindingMode().name();
        if (bindingMode != null) {
            mode = bindingMode.name();
        }
        boolean skip = configuration.isSkipBindingOnErrorCode();
        if (skipBindingOnErrorCode != null) {
            skip = skipBindingOnErrorCode;
        }

        if (mode == null || "off".equals(mode)) {
            // binding mode is off
            return null;
        }

        // setup json data format
        String name = configuration.getJsonDataFormat();
        if (name != null) {
            // must only be a name, not refer to an existing instance
            Object instance = camelContext.getRegistry().lookupByName(name);
            if (instance != null) {
                throw new IllegalArgumentException("JsonDataFormat name: " + name + " must not be an existing bean instance from the registry");
            }
        } else {
            name = "json-jackson";
        }
        // this will create a new instance as the name was not already pre-created
        DataFormat json = camelContext.resolveDataFormat(name);
        DataFormat outJson = camelContext.resolveDataFormat(name);

        // is json binding required?
        if (mode.contains("json") && json == null) {
            throw new IllegalArgumentException("JSon DataFormat " + name + " not found.");
        }

        if (json != null) {
            Class<?> clazz = null;
            if (type != null) {
                String typeName = type.endsWith("[]") ? type.substring(0, type.length() - 2) : type;
                clazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
            }
            if (clazz != null) {
                IntrospectionSupport.setProperty(camelContext.getTypeConverter(), json, "unmarshalType", clazz);
                IntrospectionSupport.setProperty(camelContext.getTypeConverter(), json, "useList", type.endsWith("[]"));
            }
            setAdditionalConfiguration(configuration, camelContext, json, "json.in.");

            Class<?> outClazz = null;
            if (outType != null) {
                String typeName = outType.endsWith("[]") ? outType.substring(0, outType.length() - 2) : outType;
                outClazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
            }
            if (outClazz != null) {
                IntrospectionSupport.setProperty(camelContext.getTypeConverter(), outJson, "unmarshalType", outClazz);
                IntrospectionSupport.setProperty(camelContext.getTypeConverter(), outJson, "useList", outType.endsWith("[]"));
            }
            setAdditionalConfiguration(configuration, camelContext, outJson, "json.out.");
        }

        // setup xml data format
        name = configuration.getXmlDataFormat();
        if (name != null) {
            // must only be a name, not refer to an existing instance
            Object instance = camelContext.getRegistry().lookupByName(name);
            if (instance != null) {
                throw new IllegalArgumentException("XmlDataFormat name: " + name + " must not be an existing bean instance from the registry");
            }
        } else {
            name = "jaxb";
        }
        // this will create a new instance as the name was not already pre-created
        DataFormat jaxb = camelContext.resolveDataFormat(name);
        DataFormat outJaxb = camelContext.resolveDataFormat(name);

        // is xml binding required?
        if (mode.contains("xml") && jaxb == null) {
            throw new IllegalArgumentException("XML DataFormat " + name + " not found.");
        }

        if (jaxb != null) {
            Class<?> clazz = null;
            if (type != null) {
                String typeName = type.endsWith("[]") ? type.substring(0, type.length() - 2) : type;
                clazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
            }
            if (clazz != null) {
                JAXBContext jc = JAXBContext.newInstance(clazz);
                IntrospectionSupport.setProperty(camelContext.getTypeConverter(), jaxb, "context", jc);
            }
            setAdditionalConfiguration(configuration, camelContext, jaxb, "xml.in.");

            Class<?> outClazz = null;
            if (outType != null) {
                String typeName = outType.endsWith("[]") ? outType.substring(0, outType.length() - 2) : outType;
                outClazz = camelContext.getClassResolver().resolveMandatoryClass(typeName);
            }
            if (outClazz != null) {
                JAXBContext jc = JAXBContext.newInstance(outClazz);
                IntrospectionSupport.setProperty(camelContext.getTypeConverter(), outJaxb, "context", jc);
            } else if (clazz != null) {
                // fallback and use the context from the input
                JAXBContext jc = JAXBContext.newInstance(clazz);
                IntrospectionSupport.setProperty(camelContext.getTypeConverter(), outJaxb, "context", jc);
            }
            setAdditionalConfiguration(configuration, camelContext, outJaxb, "xml.out.");
        }

        return new RestProducerBindingProcessor(producer, camelContext, json, jaxb, outJson, outJaxb, mode, skip, outType);
    }

    private void setAdditionalConfiguration(RestConfiguration config, CamelContext context,
                                            DataFormat dataFormat, String prefix) throws Exception {
        if (config.getDataFormatProperties() != null && !config.getDataFormatProperties().isEmpty()) {
            // must use a copy as otherwise the options gets removed during introspection setProperties
            Map<String, Object> copy = new HashMap<String, Object>();

            // filter keys on prefix
            // - either its a known prefix and must match the prefix parameter
            // - or its a common configuration that we should always use
            for (Map.Entry<String, Object> entry : config.getDataFormatProperties().entrySet()) {
                String key = entry.getKey();
                String copyKey;
                boolean known = isKeyKnownPrefix(key);
                if (known) {
                    // remove the prefix from the key to use
                    copyKey = key.substring(prefix.length());
                } else {
                    // use the key as is
                    copyKey = key;
                }
                if (!known || key.startsWith(prefix)) {
                    copy.put(copyKey, entry.getValue());
                }
            }

            // set reference properties first as they use # syntax that fools the regular properties setter
            EndpointHelper.setReferenceProperties(context, dataFormat, copy);
            EndpointHelper.setProperties(context, dataFormat, copy);
        }
    }

    private boolean isKeyKnownPrefix(String key) {
        return key.startsWith("json.in.") || key.startsWith("json.out.") || key.startsWith("xml.in.") || key.startsWith("xml.out.");
    }

    static String createQueryParameters(String query, Message inMessage) throws URISyntaxException, UnsupportedEncodingException {
        if (query != null) {
            final Map<String, Object> givenParams = URISupport.parseQuery(query);
            final Map<String, Object> params = new LinkedHashMap<>(givenParams.size());
            for (Map.Entry<String, Object> entry : givenParams.entrySet()) {
                Object v = entry.getValue();
                if (v != null) {
                    String a = v.toString();
                    // decode the key as { may be decoded to %NN
                    a = URLDecoder.decode(a, "UTF-8");
                    if (a.startsWith("{") && a.endsWith("}")) {
                        String key = a.substring(1, a.length() - 1);
                        boolean optional = false;
                        if (key.endsWith("?")) {
                            key = key.substring(0, key.length() - 1);
                            optional = true;
                        }
                        String value = inMessage.getHeader(key, String.class);
                        if (value != null) {
                            params.put(key, value);
                        } else if (!optional) {
                            // value is null and parameter is not optional
                            params.put(entry.getKey(), entry.getValue());
                        }
                    } else {
                        params.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            query = URISupport.createQueryString(params);
        }
        return query;
    }
}
