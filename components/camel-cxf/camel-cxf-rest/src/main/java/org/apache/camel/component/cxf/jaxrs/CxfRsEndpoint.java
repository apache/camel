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
package org.apache.camel.component.cxf.jaxrs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.common.NullFaultListener;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.util.ModCountCopyOnWriteArrayList;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.ext.logging.AbstractLoggingInterceptor;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.AbstractBasicInterceptorProvider;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.jaxrs.utils.ResourceUtils;
import org.apache.cxf.logging.FaultListener;
import org.apache.cxf.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.cxf.common.message.CxfConstants.SCHEME_CXF_RS;

/**
 * Expose REST services described by JAX-RS resource classes, or send requests to REST services, using Apache CXF.
 */
@UriEndpoint(firstVersion = "2.0.0", scheme = SCHEME_CXF_RS, title = "CXF-RS", syntax = "cxfrs:beanId:address",
             category = { Category.REST }, lenientProperties = true, headersClass = CxfConstants.class)
@Metadata(annotations = {
        "protocol=http",
})
@BodyTypes(consumerOut = @BodyType(value = Carrier.LIST, javaType = "org.apache.cxf.message.MessageContentsList",
                                   dependsOn = "performInvocation",
                                   byOption = {
                                           @BodyType.When(option = "bindingStyle", value = "SimpleConsumer",
                                                          carriers = Carrier.UNKNOWN),
                                           @BodyType.When(option = "bindingStyle", value = "Custom",
                                                          carriers = Carrier.UNKNOWN) },
                                   description = "Default: a MessageContentsList of the parameters of the resource method,"
                                                 + " with its name in the CamelCxfOperationName header. SimpleConsumer: the"
                                                 + " request entity as the method declares it (a String, an InputStream, a"
                                                 + " JAXB bean), with the path, query and header parameters in headers; the"
                                                 + " MessageContentsList when the method has no entity, such as a GET"),
           consumerReply = @BodyType(value = {
                   Carrier.NULL, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                   Carrier.POJO },
                                     byOption = @BodyType.When(option = "bindingStyle", value = "Custom",
                                                               carriers = Carrier.ANY),
                                     description = "The response entity: a jakarta.ws.rs.core.Response, or text, bytes, a"
                                                   + " stream, a file, or an object a JAX-RS provider writes for the"
                                                   + " Content-Type (a JAXB bean as XML); a Map or a List fails without a"
                                                   + " provider for it. With the Default binding an unchanged request"
                                                   + " MessageContentsList replies with its single element, or no content"
                                                   + " when empty (unless the method returns a collection); one of several"
                                                   + " parameters fails: set the reply body"),
           producerIn = @BodyType(value = {
                   Carrier.NULL, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                   Carrier.POJO },
                                  byOption = @BodyType.When(option = "httpClientAPI", value = "false",
                                                            carriers = {
                                                                    Carrier.LIST, Carrier.TEXT, Carrier.VALUE,
                                                                    Carrier.POJO, Carrier.NULL }),
                                  description = "The request entity, written for the Content-Type header: text, bytes, a"
                                                + " stream, a file or an object a JAX-RS provider writes, such as a List as"
                                                + " a whole (of a MessageContentsList or an Object[] only the first element"
                                                + " is sent); a"
                                                + " Map fails. A GET sends no body. With"
                                                + " httpClientAPI=false: the parameters of the resource method named by the"
                                                + " CamelCxfOperationName header, as a List or the one parameter"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = @BodyType.When(option = "httpClientAPI", value = "false",
                                                             carriers = Carrier.UNKNOWN),
                                   description = "A jakarta.ws.rs.core.Response whose entity is the response stream, read"
                                                 + " once (it converts to a String or an InputStream); the entity read as the"
                                                 + " class in the CamelCxfRsResponseClass header when that is set. With"
                                                 + " httpClientAPI=false: the return value of the resource method, null for"
                                                 + " a void one"))
public class CxfRsEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(CxfRsEndpoint.class);

    @UriParam(label = "advanced")
    protected Bus bus;

    private final InterceptorHolder interceptorHolder = new InterceptorHolder();
    private CxfRsConfigurer chainedConfigurer;

    private Map<String, String> parameters;
    private Map<String, Object> properties;

    @UriPath(description = "The id of the registry bean that defines this endpoint. Written in the path of the endpoint URI in"
                           + " place of the service address, with the prefix bean:, for example bean:rsEndpoint. The bean is a"
                           + " CxfRsEndpoint, or, with camel-cxf-spring-rest, a CXF JAXRSServerFactoryBean for the consumer or"
                           + " a JAXRSClientFactoryBean for the producer, for example from a cxf:rsServer or cxf:rsClient"
                           + " element in Spring XML. The options of the endpoint URI are applied after the settings of the"
                           + " bean. On a CxfRsEndpoint bean, they are applied to the bean itself, so endpoint URIs with"
                           + " different options must not share one CxfRsEndpoint bean.")
    private String beanId;
    @UriPath(description = "The service address, for example http://localhost:9000/rest. The consumer receives requests at"
                           + " this address, and the producer sends requests to it. The CamelDestinationOverrideUrl header"
                           + " replaces this address for a request that the producer sends.")
    private String address;
    @UriParam(description = "The JAX-RS resource classes. Written in the endpoint URI as fully qualified class names,"
                            + " separated by commas. The consumer serves them as root resources, and a resource class can be"
                            + " an interface only when performInvocation is false. Without resource classes, the consumer"
                            + " needs other resources, for example from serviceBeans or modelRef. With the proxy-based API,"
                            + " the producer calls the resource method named by the CamelCxfOperationName header on a proxy of"
                            + " the first resource class. With the WebClient API, the producer does not need resource classes.")
    private List<Class<?>> resourceClasses;
    @UriParam(label = "consumer,advanced", javaType = "java.lang.String",
              description = "The beans to serve as JAX-RS root resources of the consumer, in addition to resourceClasses."
                            + " Written in the endpoint URI as bean ids with the prefix #, separated by commas, for example"
                            + " #customerService, or as the id of one List bean, for example #myServiceBeans. The consumer"
                            + " calls the resource methods of the beans only when performInvocation is true, but always calls"
                            + " their sub-resource locators.")
    private List<Object> serviceBeans = new LinkedList<>();
    private String serviceBeansRef;
    @UriParam(description = "The location of a CXF user model, for example classpath:/model/customers.xml. The model is an XML"
                            + " document that describes resources and their operations in place of JAX-RS annotations, and the"
                            + " name attribute of each resource names its resource class. On the consumer, one resource of the"
                            + " model can be without a name attribute: the consumer gives it the interface"
                            + " org.apache.camel.component.cxf.jaxrs.DefaultModelResource, which works only when"
                            + " performInvocation is false. The producer builds requests from the model only with the"
                            + " proxy-based API.")
    private String modelRef;
    @UriParam(label = "consumer", defaultValue = "Default",
              description = "How the consumer maps the request to the Camel message and the reply to the response. Default:"
                            + " the message body is a MessageContentsList of the parameters of the resource method."
                            + " SimpleConsumer: the path, query, header, cookie, form and matrix parameters become Camel"
                            + " headers named as in their annotations, and the first other parameter, normally the request"
                            + " entity, becomes the message body. The component documentation describes SimpleConsumer in"
                            + " detail. Custom: the CxfRsBinding of the binding option maps the request and the reply. Setting"
                            + " the binding option sets bindingStyle to Custom. The producer uses the binding that Default or"
                            + " Custom selects, and cannot be created with SimpleConsumer.")
    private BindingStyle bindingStyle = BindingStyle.Default;
    @UriParam(label = "consumer",
              description = "The address to publish in the WADL that the consumer serves at the service address followed by"
                            + " ?_wadl. It replaces the base attribute of the resources element. Use it when clients reach the"
                            + " service through another address, for example when the service is behind a proxy. CXF serves"
                            + " the WADL only when the module cxf-rt-rs-service-description is on the classpath.")
    private String publishedEndpointUrl;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced",
              description = "To use a custom org.apache.camel.component.cxf.jaxrs.CxfRsBinding to map between the Camel"
                            + " message and the JAX-RS request and response, on the consumer and on the producer. Setting this"
                            + " option sets bindingStyle to Custom.")
    private CxfRsBinding binding;
    @UriParam(javaType = "java.lang.String",
              description = "The JAX-RS providers to add to the server that the consumer creates and to each client that the"
                            + " producer creates, for example a MessageBodyReader and MessageBodyWriter for JSON. Written in"
                            + " the endpoint URI as bean ids with the prefix #, separated by commas, for example"
                            + " #jsonProvider,#xmlProvider. The built-in CXF providers stay registered.")
    private List<Object> providers = new LinkedList<>();
    private String providersRef;
    @UriParam
    private List<String> schemaLocations;
    @UriParam(description = "The CXF features to add to the server that the consumer creates and to each client that the"
                            + " producer creates, as a List of org.apache.cxf.feature.Feature. Written in the endpoint URI as"
                            + " bean ids with the prefix #, separated by commas, for example #gzipFeature, or as the id of one"
                            + " List bean, for example #myFeatures.")
    private List<Feature> features = new ModCountCopyOnWriteArrayList<>();
    @UriParam(label = "producer,advanced", defaultValue = "true",
              description = "Whether to use the CXF WebClient API instead of the proxy-based API. With the WebClient API, the"
                            + " producer builds the request from the CamelHttpMethod and CamelHttpPath headers and, for"
                            + " methods such as POST and PUT, takes the request entity from the message body. With the"
                            + " proxy-based API, the producer calls the resource method named by the CamelCxfOperationName"
                            + " header on a proxy of the first resource class, with the message body as its parameters. The"
                            + " CamelCxfRsUsingHttpAPI header replaces this option for a request that the producer sends.")
    private boolean httpClientAPI = true;
    @UriParam(label = "producer,advanced",
              description = "Whether to ignore the message body for a DELETE request and send the request without an entity."
                            + " If false, the producer takes the request entity of a DELETE request from the message body, as"
                            + " for POST and PUT. The producer uses this option only with the WebClient API.")
    private boolean ignoreDeleteMethodMessageBody;
    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether to fail the exchange with an org.apache.camel.component.cxf.common.CxfOperationException"
                            + " when the HTTP status code of the response is greater than 207. The exception holds the status"
                            + " code, the response headers and the response entity. When synchronous is true, the producer"
                            + " does this only when it reads the response as a jakarta.ws.rs.core.Response. CXF itself can"
                            + " also fail the exchange for a status code of 300 or more, for example with the proxy-based API"
                            + " when the resource method does not return a Response.")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "producer,advanced", defaultValue = "10",
              description = "The maximum number of CXF JAXRSClientFactoryBean instances that the producer keeps in its cache,"
                            + " one for each address that it sends requests to: the service address, or the address in the"
                            + " CamelDestinationOverrideUrl header. When the cache is full, the producer drops the least"
                            + " recently used factory bean. The producer creates a new client from the factory bean for each"
                            + " request. The value must be greater than 0.")
    private int maxClientCacheSize = 10;
    @UriParam(label = "producer",
              description = "To use a custom org.apache.camel.support.jsse.SSLContextParameters for the HTTPS requests that"
                            + " the producer sends. If not set, the component sets it to the global SSL context parameters"
                            + " when it creates the endpoint and its own useGlobalSslContextParameters option is true. The"
                            + " consumer does not use it.")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "producer",
              description = "To use a custom javax.net.ssl.HostnameVerifier for the HTTPS requests that the producer sends."
                            + " The consumer does not use it.")
    private HostnameVerifier hostnameVerifier;
    @UriParam(label = "logging",
              description = "Whether to add a LoggingFeature, which writes the requests and responses of the endpoint to the"
                            + " log. The LoggingFeature writes them with SLF4J at INFO level to loggers whose names start with"
                            + " org.apache.cxf.services.")
    private boolean loggingFeatureEnabled;
    @UriParam(label = "logging", defaultValue = "" + AbstractLoggingInterceptor.DEFAULT_LIMIT,
              description = "The size limit at which CXF truncates the payload of a request or response in the log. Sets the"
                            + " limit of the LoggingFeature that the endpoint adds when loggingFeatureEnabled is true. Use -1"
                            + " for no limit.")
    private int loggingSizeLimit = AbstractLoggingInterceptor.DEFAULT_LIMIT;
    @UriParam(description = "Whether to skip the log entry that the PhaseInterceptorChain writes when an interceptor throws an"
                            + " exception. Sets the CXF property org.apache.cxf.logging.FaultListener to a FaultListener that"
                            + " skips this log entry. Other log entries that CXF writes while it handles the fault are not"
                            + " affected.")
    private boolean skipFaultLogging;
    @UriParam(label = "advanced", defaultValue = "30000", javaType = "java.time.Duration",
              description = "The timeout in milliseconds of the CXF continuation that the consumer uses to process the"
                            + " exchange asynchronously. If it expires before the route has completed, the consumer sets an"
                            + " ExchangeTimedOutException on the exchange and fails the request with this exception, which a"
                            + " JAX-RS ExceptionMapper can turn into a response. Use 0 for no timeout. Not used when"
                            + " synchronous is true or when the consumer gets no continuation from the transport.")
    private long continuationTimeout = 30000;
    @UriParam(label = "advanced",
              description = "Whether to set the Bus of the bus option as the CXF default bus by calling"
                            + " BusFactory.setDefaultBus. The endpoint makes this call when the bus option is set, so this"
                            + " option must be set before the bus option, which is the order in which an endpoint URI sets"
                            + " them. Without the bus option, this option has no effect, because the endpoint does not create"
                            + " a Bus.")
    private boolean defaultBus;
    @UriParam(label = "advanced",
              description = "Whether to call the resource method, on an instance of the resource class or on a bean of"
                            + " serviceBeans, before the consumer runs the route. If true, the consumer does not serve"
                            + " resource classes that are interfaces. If the method throws an exception, the route does not"
                            + " run. If the method returns a value other than null, the route gets that value as the message"
                            + " body, without the Camel headers that the binding sets from the request.")
    private boolean performInvocation;
    @UriParam(label = "advanced",
              description = "Whether to set the JAX-RS contexts of the request as exchange properties, so that the route can"
                            + " use the JAX-RS API on them. The consumer sets the UriInfo, Request, HttpHeaders and"
                            + " SecurityContext of the request, each under the fully qualified name of its interface, for"
                            + " example jakarta.ws.rs.core.UriInfo.")
    private boolean propagateContexts;
    @UriParam(label = "advanced",
              description = "To use a custom org.apache.camel.component.cxf.jaxrs.CxfRsConfigurer to configure CXF in Java"
                            + " code. Its configure method receives the CXF factory bean before a client or the server is"
                            + " created, and configureClient receives each client that the producer creates. The consumer does"
                            + " not call configureServer.")
    private CxfRsConfigurer cxfRsConfigurer;
    @UriParam(label = "producer",
              description = "To use a custom org.apache.camel.http.base.cookie.CookieHandler to keep the cookies of an HTTP"
                            + " session between the requests that the producer sends. An InstanceCookieHandler keeps the"
                            + " cookies in the handler itself, and an ExchangeCookieHandler keeps them in an exchange"
                            + " property. The consumer does not use it.")
    private CookieHandler cookieHandler;
    @UriParam(defaultValue = "false", label = "producer,advanced",
              description = "Whether to always process the exchange synchronously. If true, the producer sends each request"
                            + " without an InvocationCallback and waits for the response, and the consumer runs the route"
                            + " without a CXF continuation. The component sets it to the value of its own synchronous option"
                            + " when it creates the endpoint, including a bean: endpoint, unless the endpoint URI sets it.")
    private boolean synchronous;

    public CxfRsEndpoint() {
    }

    public CxfRsEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        setAddress(endpointUri);
    }

    public CxfRsEndpoint(Component component, String uri, AbstractJAXRSFactoryBean bean) {
        super(uri, component);
        setAddress(bean.getAddress());
        setFeatures(bean.getFeatures());
        setProperties(bean.getProperties());
        // Update the sfb address by resolving the properties
        bean.setAddress(getAddress());

    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    // This method is for CxfRsComponent setting the EndpointUri
    protected void updateEndpointUri(String endpointUri) {
        super.setEndpointUri(endpointUri);
    }

    public void setParameters(Map<String, String> param) {
        parameters = param;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setHttpClientAPI(boolean clientAPI) {
        httpClientAPI = clientAPI;
    }

    public boolean isHttpClientAPI() {
        return httpClientAPI;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CxfRsConsumer answer = new CxfRsConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (bindingStyle == BindingStyle.SimpleConsumer) {
            throw new IllegalArgumentException("The SimpleConsumer Binding Style cannot be used in a camel-cxfrs producer");
        }
        final CxfRsProducer cxfRsProducer = new CxfRsProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(cxfRsProducer);
        } else {
            return cxfRsProducer;
        }
    }

    public void setBinding(CxfRsBinding binding) {
        this.binding = binding;
    }

    public CxfRsBinding getBinding() {
        return binding;
    }

    public boolean isSkipFaultLogging() {
        return skipFaultLogging;
    }

    public CxfRsConfigurer getChainedCxfRsEndpointConfigurer() {
        return chainedConfigurer;
    }

    public void setSkipFaultLogging(boolean skipFaultLogging) {
        this.skipFaultLogging = skipFaultLogging;
    }

    protected void checkBeanType(Object object, Class<?> clazz) {
        if (!clazz.isAssignableFrom(object.getClass())) {
            throw new IllegalArgumentException("The configure bean is not the instance of " + clazz.getName());
        }
    }

    protected void setupJAXRSServerFactoryBean(JAXRSServerFactoryBean sfb) {
        // address
        if (getAddress() != null) {
            sfb.setAddress(getAddress());
        }
        processResourceModel(sfb);
        if (getResourceClasses() != null) {
            sfb.setResourceClasses(getResourceClasses());
        }

        List<Object> beans = new ArrayList<>(serviceBeans);
        if (serviceBeansRef != null) {
            beans.addAll(EndpointHelper.resolveReferenceListParameter(getCamelContext(), serviceBeansRef, Object.class));
        }
        sfb.setServiceBeans(beans);

        // setup the resource providers for interfaces
        List<ClassResourceInfo> cris = sfb.getServiceFactory().getClassResourceInfo();
        for (ClassResourceInfo cri : cris) {
            final Class<?> serviceClass = cri.getServiceClass();
            if (serviceClass.isInterface()) {
                cri.setResourceProvider(new CamelResourceProvider(serviceClass));
            }
        }
        setupCommonFactoryProperties(sfb);
        sfb.setStart(false);
        getNullSafeCxfRsEndpointConfigurer().configure(sfb);
    }

    protected CxfRsConfigurer getNullSafeCxfRsEndpointConfigurer() {
        if (cxfRsConfigurer == null) {
            return new ChainedCxfRsConfigurer.NullCxfRsConfigurer();
        }
        return cxfRsConfigurer;
    }

    private void processResourceModel(JAXRSServerFactoryBean sfb) {
        // Currently a CXF model document is the only possible source
        // of the model. Other sources will be supported going forward
        if (modelRef != null) {

            List<UserResource> resources = ResourceUtils.getUserResources(modelRef, sfb.getBus());

            processUserResources(sfb, resources);
        }
    }

    /*
     * Prepare model beans and set them on the factory.
     * The model beans can be created from a variety of sources such as
     * CXF Model extensions but also other documents (to be supported in the future).
     */
    private void processUserResources(JAXRSServerFactoryBean sfb, List<UserResource> resources) {
        for (UserResource resource : resources) {
            if (StringUtils.isEmpty(resource.getName())) {
                resource.setName(DefaultModelResource.class.getName());
            }
        }
        // The CXF to Camel exchange binding may need to be customized
        // for the operation name, request, response types be derived from
        // the model info (when a given model does provide this info) as opposed
        // to a matched method which is of no real use with a default handler.
        sfb.setModelBeans(resources);

    }

    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb, String address) {
        if (modelRef != null) {
            cfb.setModelRef(modelRef);
        }
        if (getResourceClasses() != null && !getResourceClasses().isEmpty()) {
            cfb.setResourceClass(getResourceClasses().get(0));
            cfb.getServiceFactory().setResourceClasses(getResourceClasses());
        }
        setupCommonFactoryProperties(cfb);
        cfb.setThreadSafe(true);
        getNullSafeCxfRsEndpointConfigurer().configure(cfb);
        // Add the address could be override by message header
        if (address != null) {
            cfb.setAddress(address);
        }
    }

    protected void setupCommonFactoryProperties(AbstractJAXRSFactoryBean factory) {
        // let customer to override the default setting of provider
        if (!getProviders().isEmpty()) {
            factory.setProviders(getProviders());
        }
        // setup the features
        if (!getFeatures().isEmpty()) {
            factory.getFeatures().addAll(getFeatures());
        }

        if (publishedEndpointUrl != null) {
            factory.setPublishedEndpointUrl(publishedEndpointUrl);
        }

        // we need to avoid flushing the setting from spring or blueprint
        if (!interceptorHolder.getInInterceptors().isEmpty()) {
            factory.setInInterceptors(interceptorHolder.getInInterceptors());
        }
        if (!interceptorHolder.getOutInterceptors().isEmpty()) {
            factory.setOutInterceptors(interceptorHolder.getOutInterceptors());
        }
        if (!interceptorHolder.getOutFaultInterceptors().isEmpty()) {
            factory.setOutFaultInterceptors(interceptorHolder.getOutFaultInterceptors());
        }
        if (!interceptorHolder.getInFaultInterceptors().isEmpty()) {
            factory.setInFaultInterceptors(interceptorHolder.getInFaultInterceptors());
        }

        if (getProperties() != null) {
            setupProperties(factory);
        }

        if (isLoggingFeatureEnabled()) {
            setupLoggingFeature(factory);
        }
        if (this.isSkipFaultLogging()) {
            setupSkipFaultLogging(factory);
        }
    }

    private void setupProperties(AbstractJAXRSFactoryBean factory) {
        if (factory.getProperties() != null) {
            // add to existing properties
            factory.getProperties().putAll(getProperties());
        } else {
            factory.setProperties(getProperties());
        }
        LOG.debug("JAXRS FactoryBean: {} added properties: {}", factory, getProperties());
    }

    private void setupLoggingFeature(AbstractJAXRSFactoryBean factory) {
        LoggingFeature loggingFeature = new LoggingFeature();
        if (getLoggingSizeLimit() >= -1) {
            loggingFeature.setLimit(getLoggingSizeLimit());
        }
        factory.getFeatures().add(loggingFeature);
    }

    private static void setupSkipFaultLogging(AbstractJAXRSFactoryBean factory) {
        if (factory.getProperties() == null) {
            factory.setProperties(new HashMap<>());
        }
        factory.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
    }

    protected JAXRSServerFactoryBean newJAXRSServerFactoryBean() {
        return new JAXRSServerFactoryBean() {
            @Override
            protected boolean isValidClassResourceInfo(ClassResourceInfo cri) {
                // CXF will consider interfaces created for managing model resources
                // invalid - however it is fine with Camel processors if no service invocation
                // is requested.
                return !performInvocation || !cri.getServiceClass().isInterface();
            }
        };
    }

    protected JAXRSClientFactoryBean newJAXRSClientFactoryBean() {
        return new JAXRSClientFactoryBean();
    }

    protected String resolvePropertyPlaceholders(String str) {
        try {
            if (getCamelContext() != null) {
                return getCamelContext().resolvePropertyPlaceholders(str);
            } else {
                return str;
            }
        } catch (Exception ex) {
            throw RuntimeCamelException.wrapRuntimeCamelException(ex);
        }
    }

    public JAXRSServerFactoryBean createJAXRSServerFactoryBean() {
        JAXRSServerFactoryBean answer = newJAXRSServerFactoryBean();
        setupJAXRSServerFactoryBean(answer);
        return answer;
    }

    public JAXRSClientFactoryBean createJAXRSClientFactoryBean() {
        return createJAXRSClientFactoryBean(getAddress());
    }

    public JAXRSClientFactoryBean createJAXRSClientFactoryBean(String address) {
        JAXRSClientFactoryBean answer = newJAXRSClientFactoryBean();
        setupJAXRSClientFactoryBean(answer, address);
        return answer;
    }

    public List<Class<?>> getResourceClasses() {
        return resourceClasses;
    }

    public void addResourceClass(Class<?> resourceClass) {
        if (resourceClasses == null) {
            resourceClasses = new ArrayList<>();
        }
        resourceClasses.add(resourceClass);
    }

    public void setResourceClasses(List<Class<?>> resourceClasses) {
        this.resourceClasses = resourceClasses;
    }

    public void setResourceClasses(Class<?>... classes) {
        setResourceClasses(Arrays.asList(classes));
    }

    public List<?> getServiceBeans() {
        return serviceBeans;
    }

    public void setServiceBeans(String beans) {
        this.serviceBeansRef = beans;
    }

    public void setServiceBeans(List<?> beans) {
        this.serviceBeans.addAll(beans);
    }

    public void setServiceBean(Object bean) {
        this.serviceBeans.add(bean);
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getModelRef() {
        return modelRef;
    }

    public void setModelRef(String ref) {
        this.modelRef = ref;
    }

    public String getAddress() {
        return resolvePropertyPlaceholders(address);
    }

    public String getPublishedEndpointUrl() {
        return publishedEndpointUrl;
    }

    public void setPublishedEndpointUrl(String publishedEndpointUrl) {
        this.publishedEndpointUrl = publishedEndpointUrl;
    }

    public boolean isLoggingFeatureEnabled() {
        return loggingFeatureEnabled;
    }

    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        this.loggingFeatureEnabled = loggingFeatureEnabled;
    }

    public int getLoggingSizeLimit() {
        return loggingSizeLimit;
    }

    public void setLoggingSizeLimit(int loggingSizeLimit) {
        if (loggingSizeLimit < -1) {
            throw new IllegalArgumentException("LoggingSizeLimit must be greater or equal to -1.");
        }
        this.loggingSizeLimit = loggingSizeLimit;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public void setMaxClientCacheSize(int maxClientCacheSize) {
        if (maxClientCacheSize <= 0) {
            throw new IllegalArgumentException("maxClientCacheSize must be greater than 0, was: " + maxClientCacheSize);
        }
        this.maxClientCacheSize = maxClientCacheSize;
    }

    public int getMaxClientCacheSize() {
        return maxClientCacheSize;
    }

    /**
     * To use a custom configured CXF Bus.
     */
    public void setBus(Bus bus) {
        this.bus = bus;
        if (defaultBus) {
            BusFactory.setDefaultBus(bus);
            LOG.debug("Set bus {} as thread default bus", bus);
        }
    }

    public Bus getBus() {
        return bus;
    }

    public void setDefaultBus(boolean isSetDefaultBus) {
        this.defaultBus = isSetDefaultBus;
    }

    public boolean isDefaultBus() {
        return defaultBus;
    }

    public boolean isIgnoreDeleteMethodMessageBody() {
        return ignoreDeleteMethodMessageBody;
    }

    public void setIgnoreDeleteMethodMessageBody(boolean ignoreDeleteMethodMessageBody) {
        this.ignoreDeleteMethodMessageBody = ignoreDeleteMethodMessageBody;
    }

    public BindingStyle getBindingStyle() {
        return bindingStyle;
    }

    public List<?> getProviders() {
        return providers;
    }

    public void setProviders(List<?> providers) {
        this.providers.addAll(providers);
    }

    public void setProviders(String providers) {
        this.providersRef = providers;
    }

    /**
     * Set custom JAX-RS provider to the CxfRs endpoint.
     */
    public void setProvider(Object provider) {
        providers.add(provider);
    }

    /**
     * Sets the locations of the schema(s) which can be used to validate the incoming XML or JAXB-driven JSON.
     */
    public void setSchemaLocation(String schema) {
        setSchemaLocations(Collections.singletonList(schema));
    }

    /**
     * Sets the locations of the schema(s) which can be used to validate the incoming XML or JAXB-driven JSON.
     */
    public void setSchemaLocations(List<String> schemas) {
        this.schemaLocations = schemas;
    }

    public List<String> getSchemaLocations() {
        return schemaLocations;
    }

    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return interceptorHolder.getOutFaultInterceptors();
    }

    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return interceptorHolder.getInFaultInterceptors();
    }

    public List<Interceptor<? extends Message>> getInInterceptors() {
        return interceptorHolder.getInInterceptors();
    }

    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return interceptorHolder.getOutInterceptors();
    }

    /**
     * Set the inInterceptors to the CxfRs endpoint.
     */
    public void setInInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setInInterceptors(interceptors);
    }

    /**
     * Set the inFaultInterceptors to the CxfRs endpoint.
     */
    public void setInFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setInFaultInterceptors(interceptors);
    }

    /**
     * Set the outInterceptor to the CxfRs endpoint.
     */
    public void setOutInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setOutInterceptors(interceptors);
    }

    /**
     * Set the outFaultInterceptors to the CxfRs endpoint.
     */
    public void setOutFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setOutFaultInterceptors(interceptors);
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = new ArrayList<>(features);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        if (this.properties == null) {
            this.properties = properties;
        } else {
            this.properties.putAll(properties);
        }
    }

    public void setBindingStyle(BindingStyle bindingStyle) {
        this.bindingStyle = bindingStyle;
    }

    public String getBeanId() {
        return beanId;
    }

    public void setBeanId(String beanId) {
        this.beanId = beanId;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (headerFilterStrategy == null) {
            headerFilterStrategy = new CxfRsHeaderFilterStrategy();
        }

        // if the user explicitly selected the Custom binding style, he must provide a binding
        if (bindingStyle == BindingStyle.Custom && binding == null) {
            throw new IllegalArgumentException("Custom binding style selected, but no binding was supplied");
        }

        // if the user has set a binding, do nothing, just make sure that BindingStyle = Custom for coherency purposes
        if (binding != null) {
            bindingStyle = BindingStyle.Custom;
        }

        // set the right binding based on the binding style
        if (bindingStyle == BindingStyle.SimpleConsumer) {
            binding = new SimpleCxfRsBinding();
        } else if (bindingStyle == BindingStyle.Custom) {
            // do nothing
        } else {
            binding = new DefaultCxfRsBinding();
        }

        if (binding instanceof HeaderFilterStrategyAware headerFilterStrategyAware) {
            headerFilterStrategyAware.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }

        if (providersRef != null) {
            String[] names = providersRef.split(",");
            for (String name : names) {
                Object provider = EndpointHelper.resolveReferenceParameter(getCamelContext(), name, Object.class, true);
                setProvider(provider);
            }
        }

        chainedConfigurer = ChainedCxfRsConfigurer
                .create(getNullSafeCxfRsEndpointConfigurer(),
                        SslCxfRsConfigurer.create(sslContextParameters, getCamelContext()))
                .addChild(HostnameVerifierCxfRsConfigurer.create(hostnameVerifier));
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public long getContinuationTimeout() {
        return continuationTimeout;
    }

    public void setContinuationTimeout(long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    public boolean isPerformInvocation() {
        return performInvocation;
    }

    public void setPerformInvocation(boolean performInvocation) {
        this.performInvocation = performInvocation;
    }

    public boolean isPropagateContexts() {
        return propagateContexts;
    }

    public void setPropagateContexts(boolean propagateContexts) {
        this.propagateContexts = propagateContexts;
    }

    private static class InterceptorHolder extends AbstractBasicInterceptorProvider {
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public CxfRsConfigurer getCxfRsConfigurer() {
        return cxfRsConfigurer;
    }

    public void setCxfRsConfigurer(CxfRsConfigurer configurer) {
        this.cxfRsConfigurer = configurer;
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    public void setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }
}
