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
package org.apache.camel.component.cxf.jaxws;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.xml.ws.Provider;
import jakarta.xml.ws.WebServiceProvider;
import jakarta.xml.ws.handler.Handler;

import javax.net.ssl.HostnameVerifier;
import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.CamelContext;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.common.CxfBinding;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.common.NullFaultListener;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.feature.CXFMessageDataFormatFeature;
import org.apache.camel.component.cxf.feature.PayLoadDataFormatFeature;
import org.apache.camel.component.cxf.feature.RAWDataFormatFeature;
import org.apache.camel.component.cxf.transport.header.CxfHeaderFilterStrategy;
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
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.BindingConfiguration;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.injection.ResourceInjector;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.common.util.ModCountCopyOnWriteArrayList;
import org.apache.cxf.common.util.ReflectionUtil;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.databinding.DataBinding;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.ext.logging.AbstractLoggingInterceptor;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsClientFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.jaxws.context.WebServiceContextResourceResolver;
import org.apache.cxf.jaxws.handler.AnnotationHandlerChainBuilder;
import org.apache.cxf.jaxws.support.JaxWsEndpointImpl;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.logging.FaultListener;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.resource.DefaultResourceManager;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.resource.ResourceResolver;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.staxutils.StaxSource;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.transport.http.HttpDestinationFactory;
import org.apache.cxf.wsdl.WSDLManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.cxf.common.message.CxfConstants.SCHEME_CXF;

/**
 * Expose SOAP WebServices using Apache CXF or connect to external WebServices using CXF WS client.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = SCHEME_CXF, title = "CXF", syntax = "cxf:beanId:address",
             category = { Category.HTTP, Category.WEBSERVICE }, headersClass = CxfConstants.class)
@Metadata(annotations = {
        "protocol=http",
})
@BodyTypes(consumerOut = @BodyType(value = { Carrier.LIST, Carrier.NULL },
                                   byOption = {
                                           @BodyType.When(option = "dataFormat", value = "PAYLOAD", carriers = Carrier.POJO,
                                                          javaType = "org.apache.camel.component.cxf.common.CxfPayload"),
                                           @BodyType.When(option = "dataFormat", value = "RAW", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "dataFormat", value = "MESSAGE", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "dataFormat", value = "CXF_MESSAGE", carriers = Carrier.POJO,
                                                          javaType = "jakarta.xml.soap.SOAPMessage") },
                                   description = "POJO: a MessageContentsList of the parameters of the operation, typed as the"
                                                 + " service class declares them, with its name in the CamelCxfOperationName"
                                                 + " header; null for an operation without parameters. PAYLOAD: a CxfPayload"
                                                 + " of the elements of the soap:Body and the SOAP headers. RAW: the stream of"
                                                 + " the whole SOAP envelope, without the operation name. CXF_MESSAGE: the SAAJ"
                                                 + " SOAPMessage"),
           consumerReply = @BodyType(value = { Carrier.TEXT, Carrier.VALUE, Carrier.POJO, Carrier.LIST, Carrier.NULL },
                                     byOption = {
                                             @BodyType.When(option = "dataFormat", value = "PAYLOAD",
                                                            carriers = {
                                                                    Carrier.POJO, Carrier.TEXT, Carrier.BYTES,
                                                                    Carrier.STREAM, Carrier.NULL }),
                                             @BodyType.When(option = "dataFormat", value = "RAW",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE, Carrier.NULL }),
                                             @BodyType.When(option = "dataFormat", value = "MESSAGE",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE, Carrier.NULL }),
                                             @BodyType.When(option = "dataFormat", value = "CXF_MESSAGE",
                                                            carriers = {
                                                                    Carrier.POJO, Carrier.TEXT, Carrier.BYTES,
                                                                    Carrier.STREAM }) },
                                     description = "POJO: the return value of the operation, of the type the service class"
                                                   + " declares (a byte array is one value), or a List or an Object array of it"
                                                   + " and the out and holder parameters; another type is a SOAP fault, so the"
                                                   + " request MessageContentsList only fits an echo. PAYLOAD: a CxfPayload, or"
                                                   + " the XML of the soap:Body contents as text, bytes, a stream or a DOM. RAW:"
                                                   + " the whole SOAP envelope as text, bytes, a stream or a file. CXF_MESSAGE: a"
                                                   + " SOAPMessage, a javax.xml.transform Source, or the whole SOAP envelope as"
                                                   + " text, bytes, a stream or a DOM. A body that does not convert is a SOAP"
                                                   + " fault"),
           producerIn = @BodyType(value = { Carrier.LIST, Carrier.TEXT, Carrier.VALUE, Carrier.POJO, Carrier.NULL },
                                  byOption = {
                                          @BodyType.When(option = "dataFormat", value = "PAYLOAD",
                                                         carriers = {
                                                                 Carrier.POJO, Carrier.TEXT, Carrier.BYTES,
                                                                 Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "dataFormat", value = "RAW",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                 Carrier.FILE }),
                                          @BodyType.When(option = "dataFormat", value = "MESSAGE",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                 Carrier.FILE }),
                                          @BodyType.When(option = "dataFormat", value = "CXF_MESSAGE",
                                                         carriers = {
                                                                 Carrier.POJO, Carrier.TEXT, Carrier.BYTES,
                                                                 Carrier.STREAM }) },
                                  description = "POJO: the parameters of the operation (the CamelCxfOperationName header or"
                                                + " defaultOperationName) as a List (or another Collection) or an Object array,"
                                                + " or the one parameter as the body; each of the type the service class"
                                                + " declares. Any other body, such as text or a byte array, is the one"
                                                + " parameter. PAYLOAD: a CxfPayload, or the XML of the soap:Body contents as"
                                                + " text, bytes, a stream, a file or a DOM. RAW: the whole SOAP envelope."
                                                + " CXF_MESSAGE: a SOAPMessage, a javax.xml.transform Source, or the whole SOAP"
                                                + " envelope as text, bytes, a stream or a DOM; another body fails"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.NULL, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "dataFormat", value = "PAYLOAD", carriers = Carrier.POJO,
                                                          javaType = "org.apache.camel.component.cxf.common.CxfPayload"),
                                           @BodyType.When(option = "dataFormat", value = "RAW", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "dataFormat", value = "MESSAGE", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "dataFormat", value = "CXF_MESSAGE", carriers = Carrier.POJO,
                                                          javaType = "jakarta.xml.soap.SOAPMessage") },
                                   description = "POJO: a MessageContentsList with the return value of the operation first,"
                                                 + " then the out and holder parameters; null for a void operation, and the"
                                                 + " body unchanged for a one-way one. PAYLOAD: a CxfPayload of the"
                                                 + " soap:Body contents. RAW: the stream of the SOAP envelope of the"
                                                 + " response, read once. CXF_MESSAGE: the SAAJ SOAPMessage"))
public class CxfEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware, Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(CxfEndpoint.class);

    @UriParam(label = "advanced")
    protected volatile Bus bus;
    @UriParam(label = "advanced",
              description = "Whether to set the Bus of this endpoint as the CXF default bus by calling"
                            + " BusFactory.setDefaultBus. This applies to a Bus set with the bus option as well as to a Bus"
                            + " that the endpoint creates.")
    protected boolean defaultBus;
    protected volatile boolean createBus;
    protected final AtomicBoolean getBusHasBeenCalled = new AtomicBoolean();

    private BindingConfiguration bindingConfig;
    private DataBinding dataBinding;
    private Object serviceFactoryBean;
    private List<Interceptor<? extends Message>> in = new ModCountCopyOnWriteArrayList<>();
    private List<Interceptor<? extends Message>> out = new ModCountCopyOnWriteArrayList<>();
    private List<Interceptor<? extends Message>> outFault = new ModCountCopyOnWriteArrayList<>();
    private List<Interceptor<? extends Message>> inFault = new ModCountCopyOnWriteArrayList<>();
    private List<Feature> features = new ModCountCopyOnWriteArrayList<>();
    private List<Handler> handlers;
    private List<String> schemaLocations;
    private String transportId;

    @UriPath(description = "The id of a CxfEndpoint bean in the registry to use as this endpoint. Written in the path of the"
                           + " endpoint URI in place of the service address, with the prefix bean:, for example"
                           + " bean:orderEndpoint. The component sets the options of the endpoint URI on that bean, so"
                           + " endpoint URIs with different options must not share one bean instance.")
    private String beanId;
    @UriParam(defaultValue = "POJO",
              description = "How the SOAP message is represented in the Camel message body. POJO: the parameters of the"
                            + " operation as Java objects (requires serviceClass). PAYLOAD: the contents of the soap:Body as a"
                            + " CxfPayload. RAW: the raw message stream from the transport layer, with most CXF interceptors"
                            + " removed, so SOAP headers are not available. CXF_MESSAGE: a SAAJ SOAPMessage, processed by the"
                            + " full set of CXF interceptors. MESSAGE is deprecated and is the same as RAW.")
    private DataFormat dataFormat = DataFormat.POJO;
    @UriPath(label = "service",
             description = "The service address, for example http://localhost:9000/orders. The consumer receives requests at"
                           + " this address, and the producer sends requests to it. The CamelDestinationOverrideUrl header"
                           + " replaces this address for a request that the producer sends over HTTP.")
    private String address;
    @UriParam(label = "service")
    private String wsdlURL;
    @UriParam(label = "service",
              description = "The Java class of the service, for example a service endpoint interface or a class that"
                            + " implements one. Written as a fully qualified class name, for example"
                            + " org.example.orders.OrderService. Required when dataFormat is POJO, which is the default."
                            + " Without a WSDL, CXF builds the service from this class.")
    private Class<?> serviceClass;
    @UriParam(label = "service",
              description = "The WSDL port, which is the name attribute of the wsdl:port element. Written as a QName in the"
                            + " form {namespaceURI}localPart, for example {http://example.org/orders}OrderPort.")
    private String portName;
    private transient QName portNameQName;
    @UriParam(label = "service",
              description = "The WSDL service, which is the name attribute of the wsdl:service element. Written as a QName in"
                            + " the form {namespaceURI}localPart, for example {http://example.org/orders}OrderService. If not"
                            + " set and wsdlURL points to a WSDL with one service, that service is used.")
    private String serviceName;
    private transient QName serviceNameQName;
    @UriParam(label = "service",
              description = "The id of the CXF binding, which is a namespace URI, for example"
                            + " http://cxf.apache.org/bindings/xformat for the XML binding without a SOAP envelope. If not"
                            + " set, CXF uses the binding of the wsdl:port. The id http://schemas.xmlsoap.org/wsdl/soap12/"
                            + " selects SOAP 1.2 when there is no WSDL.")
    private String bindingId;
    @UriParam(label = "service",
              description = "The address to publish in the WSDL that the consumer serves at the service address followed by"
                            + " ?wsdl. It replaces the location of the address element in the wsdl:port. Use it when clients"
                            + " reach the service through another address, for example when the service is behind a proxy.")
    private String publishedEndpointUrl;
    @UriParam(label = "producer",
              description = "The name of the operation that the producer invokes. Used when the Camel message has no"
                            + " CamelCxfOperationName header. If neither the CamelCxfOperationName header nor this option is"
                            + " set, the producer invokes the first operation that CXF returns for the service, in an order"
                            + " that is not defined. If neither wsdlURL nor serviceClass is set, the producer sets this option"
                            + " to Invoke.")
    private String defaultOperationName;
    @UriParam(label = "producer",
              description = "The namespace of the operation named by the CamelCxfOperationName header or by"
                            + " defaultOperationName. Used when the Camel message has no CamelCxfOperationNamespace header. If"
                            + " neither the CamelCxfOperationNamespace header nor this option is set, the producer uses the"
                            + " namespace of the service, which is the namespace of serviceName when serviceName is set. If"
                            + " neither wsdlURL nor serviceClass is set, the producer sets this option to"
                            + " http://camel.apache.org/cxf/jaxws/dispatch.")
    private String defaultOperationNamespace;
    @UriParam(label = "producer",
              description = "Whether to invoke a document-literal wrapped operation in its wrapped form instead of its"
                            + " unwrapped form. If true and dataFormat is POJO, the Camel message body of the request must"
                            + " hold the wrapper object in place of the parameters of the operation. The consumer does not use"
                            + " it.")
    private boolean wrapped;
    @UriParam(label = "producer",
              description = "To use a custom org.apache.camel.support.jsse.SSLContextParameters for the HTTPS requests that"
                            + " the producer sends. If not set, the component sets it to the global SSL context parameters"
                            + " when it creates the endpoint and its own useGlobalSslContextParameters option is true. Not"
                            + " used for reading the WSDL that wsdlURL points to. The consumer does not use it.")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "producer",
              description = "To use a custom javax.net.ssl.HostnameVerifier for the HTTPS requests that the producer sends."
                            + " Not used for reading the WSDL that wsdlURL points to. The consumer does not use it.")
    private HostnameVerifier hostnameVerifier;
    @UriParam(description = "Whether to use the document-literal wrapped style instead of the document-literal bare style for"
                            + " the operations of serviceClass. Not used for RPC-style operations or when serviceClass is a"
                            + " Provider. Otherwise, if not set, CXF uses the parameterStyle of the SOAPBinding annotation,"
                            + " and the wrapped style when there is none. The consumer does not use it when dataFormat is"
                            + " CXF_MESSAGE.")
    private Boolean wrappedStyle;
    @UriParam(label = "advanced",
              description = "Whether to allow CXF to keep the last message part of an incoming SOAP message as a"
                            + " javax.xml.transform.Source read from the stream, instead of parsing it into a DOM. Only used"
                            + " when dataFormat is PAYLOAD. The component sets it to the value of its own allowStreaming"
                            + " option when it creates the endpoint, including a bean: endpoint, unless the endpoint URI sets"
                            + " it. If not set, the endpoint uses the JVM system property"
                            + " org.apache.camel.component.cxf.streaming, and allows streaming when that property is not set"
                            + " either.")
    private Boolean allowStreaming;
    @UriParam(label = "advanced")
    private CxfBinding cxfBinding;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "logging",
              description = "Whether to add a LoggingFeature, which writes the incoming and outgoing SOAP messages of the"
                            + " endpoint to the log. The LoggingFeature writes them with SLF4J at INFO level to loggers whose"
                            + " names start with org.apache.cxf.services.")
    private boolean loggingFeatureEnabled;
    @UriParam(label = "logging", defaultValue = "" + AbstractLoggingInterceptor.DEFAULT_LIMIT,
              description = "The size limit at which CXF truncates a SOAP message in the log. Sets the limit of the"
                            + " LoggingFeature that the endpoint adds when loggingFeatureEnabled is true. Use -1 for no limit.")
    private int loggingSizeLimit = AbstractLoggingInterceptor.DEFAULT_LIMIT;
    @UriParam(label = "advanced",
              description = "Whether to send SOAP messages with MTOM, so that CXF writes them as XOP packages. Sets the"
                            + " exchange property mtom-enabled to the value of this option. The producer copies the property"
                            + " to the CXF request message, and the consumer copies it to the CXF response message. When"
                            + " dataFormat is RAW, CXF does not write the Camel message body as an XOP package.")
    private boolean mtomEnabled;
    @UriParam(label = "advanced",
              description = "Whether to skip the check that each element in the SOAP body matches the message part that the"
                            + " operation declares. Sets the CXF property soap.no.validate.parts to true. This is not XML"
                            + " schema validation, which schemaValidationEnabled controls. If dataFormat is PAYLOAD and"
                            + " neither wsdlURL nor serviceClass is set, the producer sets this option to true.")
    private boolean skipPayloadMessagePartCheck;
    @UriParam(label = "logging",
              description = "Whether to skip the log entry that the PhaseInterceptorChain writes when an interceptor throws an"
                            + " exception, for example the exception of a failed exchange of the consumer. Sets the CXF"
                            + " property org.apache.cxf.logging.FaultListener to a FaultListener that skips this log entry.")
    private boolean skipFaultLogging;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether to hide the exception of a failed exchange from the client. If true, the fault that the"
                            + " consumer returns does not contain the class or the message of the exception. This does not"
                            + " apply to a CXF Fault or an exception with the WebFault annotation, nor to a Camel message body"
                            + " that is a Throwable or a CxfPayload with a soap:Fault.")
    private boolean muteException = true;
    @UriParam(label = "advanced",
              description = "Whether to copy a protocol header to the Camel message as one String instead of a List when it"
                            + " has more than one value. The values are joined by a comma and a space. This applies to the"
                            + " request that the consumer receives and to the response that the producer receives. Sets the"
                            + " exchange property CamelCxfProtocolHeadersMerged to true.")
    private boolean mergeProtocolHeaders;
    @UriParam(label = "advanced",
              description = "To use a custom org.apache.camel.component.cxf.jaxws.CxfConfigurer to configure CXF in Java"
                            + " code. Its configure method receives the CXF factory bean before the client or server is"
                            + " created, and configureClient and configureServer receive the created Client or Server.")
    private CxfConfigurer cxfConfigurer;
    @UriParam(label = "advanced", defaultValue = "30000", javaType = "java.time.Duration",
              description = "The timeout in milliseconds of the CXF continuation that the consumer uses to process the"
                            + " exchange asynchronously. If it expires before the route has completed, the consumer returns a"
                            + " fault to the client and sets an ExchangeTimedOutException on the exchange. Use 0 for no"
                            + " timeout. Not used when synchronous is true or when the consumer gets no continuation from the"
                            + " transport, for example with the JMS transport.")
    private long continuationTimeout = 30000;
    @UriParam(label = "security", security = "secret",
              description = "The user name for HTTP Basic authentication. The producer puts it, together with the password, in"
                            + " the protocol header Authorization of its HTTP requests. Only used when the password option is"
                            + " set. Not used for reading the WSDL that wsdlURL points to. The consumer does not use it.")
    private String username;
    @UriParam(label = "security", security = "secret",
              description = "The password for HTTP Basic authentication. The producer puts it, together with the user name, in"
                            + " the protocol header Authorization of its HTTP requests. Only used when the username option is"
                            + " set. Not used for reading the WSDL that wsdlURL points to. The consumer does not use it.")
    private String password;
    @UriParam(label = "advanced", prefix = "properties.", multiValue = true)
    private Map<String, Object> properties;
    @UriParam(label = "producer",
              description = "To use a custom org.apache.camel.http.base.cookie.CookieHandler to keep the cookies of an HTTP"
                            + " session between the requests that the producer sends. An InstanceCookieHandler keeps the"
                            + " cookies in the handler itself, and an ExchangeCookieHandler keeps them in an exchange"
                            + " property. The consumer does not use it.")
    private CookieHandler cookieHandler;
    @UriParam(defaultValue = "false", label = "producer,advanced",
              description = "Whether to always process the exchange synchronously. If true, the producer invokes the CXF"
                            + " Client without a callback and waits until the invocation returns, and the consumer runs the"
                            + " route without a CXF continuation. The component sets it to the value of its own synchronous"
                            + " option when it creates the endpoint, including a bean: endpoint, unless the endpoint URI sets"
                            + " it.")
    private boolean synchronous;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Whether to validate the incoming and outgoing SOAP messages of the endpoint against the XML"
                            + " schema of the service. Sets the CXF property schema-validation-enabled to the value of this"
                            + " option. If not set, CXF validates only when another setting enables validation, for example"
                            + " the SchemaValidation annotation on serviceClass. When dataFormat is RAW, CXF does not validate"
                            + " the messages.")
    private Boolean schemaValidationEnabled;

    public CxfEndpoint() {
        setExchangePattern(ExchangePattern.InOut);
    }

    public CxfEndpoint(String remaining, CxfComponent cxfComponent) {
        super(remaining, cxfComponent);
        setAddress(remaining);
        setExchangePattern(ExchangePattern.InOut);
    }

    public CxfEndpoint copy() {
        try {
            return (CxfEndpoint) this.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    // This method is for CxfComponent setting the EndpointUri
    protected void updateEndpointUri(String endpointUri) {
        super.setEndpointUri(UnsafeUriCharactersEncoder.encodeHttpURI(endpointUri));
    }

    @Override
    public Producer createProducer() throws Exception {
        Producer answer = new CxfProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CxfConsumer answer = new CxfConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    /**
     * Populate server factory bean
     */
    protected void setupServerFactoryBean(ServerFactoryBean sfb, Class<?> cls) {

        // address
        sfb.setAddress(getAddress());

        sfb.setServiceClass(cls);

        sfb.setInInterceptors(in);
        sfb.setOutInterceptors(out);
        sfb.setOutFaultInterceptors(outFault);
        sfb.setInFaultInterceptors(inFault);
        sfb.setFeatures(features);
        if (schemaLocations != null) {
            sfb.setSchemaLocations(schemaLocations);
        }
        if (bindingConfig != null) {
            sfb.setBindingConfig(bindingConfig);
        }

        if (dataBinding != null) {
            sfb.setDataBinding(dataBinding);
        }

        if (serviceFactoryBean != null) {
            setServiceFactory(sfb, serviceFactoryBean);
        }

        if (sfb instanceof JaxWsServerFactoryBean jaxWsServerFactoryBean && handlers != null) {
            jaxWsServerFactoryBean.setHandlers(handlers);
        }
        if (getTransportId() != null) {
            sfb.setTransportId(getTransportId());
        }
        if (getBindingId() != null) {
            sfb.setBindingId(getBindingId());
        }

        // wsdl url
        if (getWsdlURL() != null) {
            sfb.setWsdlURL(getWsdlURL());
        }

        // service name qname
        if (getServiceNameAsQName() != null) {
            sfb.setServiceName(getServiceNameAsQName());
        }

        // port qname
        if (getPortNameAsQName() != null) {
            sfb.setEndpointName(getPortNameAsQName());
        }

        // apply feature here
        if (!CxfEndpointUtils.hasAnnotation(cls, WebServiceProvider.class)) {
            applyFeatures(sfb);
        } else {
            LOG.debug("Ignore DataFormat mode {} since SEI class is annotated with WebServiceProvider", getDataFormat());
        }

        if (isLoggingFeatureEnabled()) {
            enableLoggingFeature(sfb);
        }

        if (getDataFormat() == DataFormat.PAYLOAD) {
            sfb.setDataBinding(new HybridSourceDataBinding());
        }

        // set the document-literal wrapped style
        if (getWrappedStyle() != null && getDataFormat().dealias() != DataFormat.CXF_MESSAGE) {
            setWrapped(sfb, getWrappedStyle());
        }

        // any optional properties
        if (getProperties() != null) {
            applyOptionalProperties(sfb);
        }
        if (this.isSkipPayloadMessagePartCheck()) {
            applySkipPayloadMessagePartCheck(sfb);
        }

        if (this.isSkipFaultLogging()) {
            applySkipFaultLogging(sfb);
        }

        if (this.getSchemaValidationEnabled() != null) {
            applySchemaValidation(sfb);
        }

        sfb.setBus(getBus());
        sfb.setStart(false);

        // add custom http destination factories
        var factories = getCamelContext().getRegistry().findByType(HttpDestinationFactory.class);
        for (var factory : factories) {
            sfb.getBus().setExtension(factory, HttpDestinationFactory.class);
        }

        getNullSafeCxfConfigurer().configure(sfb);
    }

    private static void applySkipPayloadMessagePartCheck(ServerFactoryBean sfb) {
        if (sfb.getProperties() == null) {
            sfb.setProperties(new HashMap<>());
        }
        sfb.getProperties().put("soap.no.validate.parts", Boolean.TRUE);
    }

    private void applySchemaValidation(ServerFactoryBean sfb) {
        if (sfb.getProperties() == null) {
            sfb.setProperties(new HashMap<>());
        }
        sfb.getProperties().put(Message.SCHEMA_VALIDATION_ENABLED, schemaValidationEnabled);
    }

    private static void applySkipFaultLogging(ServerFactoryBean sfb) {
        if (sfb.getProperties() == null) {
            sfb.setProperties(new HashMap<>());
        }
        sfb.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
    }

    private void applyOptionalProperties(ServerFactoryBean sfb) {
        if (sfb.getProperties() != null) {
            // add to existing properties
            sfb.getProperties().putAll(getProperties());
        } else {
            sfb.setProperties(getProperties());
        }
        LOG.debug("ServerFactoryBean: {} added properties: {}", sfb, getProperties());
    }

    private void enableLoggingFeature(ServerFactoryBean sfb) {
        LoggingFeature loggingFeature = new LoggingFeature();
        if (getLoggingSizeLimit() >= -1) {
            loggingFeature.setLimit(getLoggingSizeLimit());
        }
        sfb.getFeatures().add(loggingFeature);
    }

    private void applyFeatures(ServerFactoryBean sfb) {
        if (getDataFormat() == DataFormat.PAYLOAD) {
            applyPayload(sfb);
        } else if (getDataFormat().dealias() == DataFormat.CXF_MESSAGE) {
            applyCXFMessage(sfb);
        } else if (getDataFormat().dealias() == DataFormat.RAW) {
            applyRaw(sfb);
        }
    }

    private void applyPayload(ServerFactoryBean sfb) {
        sfb.getFeatures().add(new PayLoadDataFormatFeature(allowStreaming));
    }

    private static void applyCXFMessage(ServerFactoryBean sfb) {
        sfb.getFeatures().add(new CXFMessageDataFormatFeature());
        sfb.setDataBinding(new SourceDataBinding());
    }

    private void applyRaw(ServerFactoryBean sfb) {
        RAWDataFormatFeature feature = new RAWDataFormatFeature();
        if (this.getExchangePattern().equals(ExchangePattern.InOnly)) {
            //if DataFormat is RAW|MESSAGE, can't read message so can't
            //determine it's oneway so need get the MEP from URI explicitly
            feature.setOneway(true);
        }
        feature.addInIntercepters(getInInterceptors());
        feature.addOutInterceptors(getOutInterceptors());
        sfb.getFeatures().add(feature);
    }

    /**
     * Create a client factory bean object. Notice that the serviceClass <b>must</b> be an interface.
     */
    public ClientFactoryBean createClientFactoryBean(Class<?> cls) {
        if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            return new JaxWsClientFactoryBean() {
                @Override
                protected Client createClient(Endpoint ep) {
                    return new CamelCxfClientImpl(getBus(), ep);
                }
            };
        } else {
            return new ClientFactoryBean() {
                @Override
                protected Client createClient(Endpoint ep) {
                    return new CamelCxfClientImpl(getBus(), ep);
                }
            };
        }
    }

    /**
     * Create a client factory bean object without serviceClass interface.
     */
    protected ClientFactoryBean createClientFactoryBean() {
        ClientFactoryBean cf = new ClientFactoryBean() {
            @Override
            protected Client createClient(Endpoint ep) {
                return new CamelCxfClientImpl(getBus(), ep);
            }

            @Override
            protected void initializeAnnotationInterceptors(Endpoint ep, Class<?> cls) {
                // Do nothing here
            }
        };
        for (Method m : cf.getClass().getMethods()) {
            if ("setServiceFactory".equals(m.getName())) {
                try {
                    // Set Object class as the service class of WSDLServiceFactoryBean
                    ReflectionUtil.setAccessible(m).invoke(cf, new WSDLServiceFactoryBean(Object.class));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return cf;
    }

    protected void setupHandlers(ClientFactoryBean factoryBean, Client client)
            throws Exception {

        if (handlers != null) {
            AnnotationHandlerChainBuilder builder = new AnnotationHandlerChainBuilder();
            Method m = factoryBean.getClass().getMethod("getServiceFactory");
            JaxWsServiceFactoryBean sf = (JaxWsServiceFactoryBean) m.invoke(factoryBean);
            @SuppressWarnings("rawtypes")
            List<Handler> chain = new ArrayList<>(handlers);

            chain.addAll(builder.buildHandlerChainFromClass(sf.getServiceClass(),
                    sf.getEndpointInfo().getName(),
                    sf.getServiceQName(),
                    factoryBean.getBindingId()));

            if (!chain.isEmpty()) {
                ResourceManager resourceManager = getBus().getExtension(ResourceManager.class);
                List<ResourceResolver> resolvers = resourceManager.getResourceResolvers();
                resourceManager = new DefaultResourceManager(resolvers);
                resourceManager.addResourceResolver(new WebServiceContextResourceResolver());
                ResourceInjector injector = new ResourceInjector(resourceManager);
                for (Handler<?> h : chain) {
                    if (Proxy.isProxyClass(h.getClass()) && getServiceClass() != null) {
                        injector.inject(h, getServiceClass());
                        injector.construct(h, getServiceClass());
                    } else {
                        injector.inject(h);
                        injector.construct(h);
                    }
                }
            }

            ((JaxWsEndpointImpl) client.getEndpoint()).getJaxwsBinding().setHandlerChain(chain);
        }
    }

    protected void setupClientFactoryBean(ClientFactoryBean factoryBean, Class<?> cls) {
        if (cls != null) {
            factoryBean.setServiceClass(cls);
        }
        factoryBean.setInInterceptors(in);
        factoryBean.setOutInterceptors(out);
        factoryBean.setOutFaultInterceptors(outFault);
        factoryBean.setInFaultInterceptors(inFault);
        factoryBean.setFeatures(features);
        factoryBean.setTransportId(transportId);
        factoryBean.setBindingId(bindingId);

        if (bindingConfig != null) {
            factoryBean.setBindingConfig(bindingConfig);
        }

        if (dataBinding != null) {
            factoryBean.setDataBinding(dataBinding);
        }

        if (serviceFactoryBean != null) {
            setServiceFactory(factoryBean, serviceFactoryBean);
        }

        // address
        factoryBean.setAddress(getAddress());

        // wsdl url
        if (getWsdlURL() != null) {
            factoryBean.setWsdlURL(getWsdlURL());
        }

        // service name qname
        if (getServiceNameAsQName() != null) {
            factoryBean.setServiceName(getServiceNameAsQName());
        }

        // port name qname
        if (getPortNameAsQName() != null) {
            factoryBean.setEndpointName(getPortNameAsQName());
        }

        // apply feature here
        if (getDataFormat().dealias() == DataFormat.RAW) {
            RAWDataFormatFeature feature = new RAWDataFormatFeature();
            feature.addInIntercepters(getInInterceptors());
            feature.addOutInterceptors(getOutInterceptors());
            factoryBean.getFeatures().add(feature);
        } else if (getDataFormat().dealias() == DataFormat.CXF_MESSAGE) {
            factoryBean.getFeatures().add(new CXFMessageDataFormatFeature());
            factoryBean.setDataBinding(new SourceDataBinding());
        } else if (getDataFormat() == DataFormat.PAYLOAD) {
            factoryBean.getFeatures().add(new PayLoadDataFormatFeature(allowStreaming));
            factoryBean.setDataBinding(new HybridSourceDataBinding());
        }

        if (isLoggingFeatureEnabled()) {
            LoggingFeature loggingFeature = new LoggingFeature();
            if (getLoggingSizeLimit() >= -1) {
                loggingFeature.setLimit(getLoggingSizeLimit());

            }
            factoryBean.getFeatures().add(loggingFeature);
        }

        // set the document-literal wrapped style
        if (getWrappedStyle() != null) {
            setWrapped(factoryBean, getWrappedStyle());
        }

        // any optional properties
        if (getProperties() != null) {
            if (factoryBean.getProperties() != null) {
                // add to existing properties
                factoryBean.getProperties().putAll(getProperties());
            } else {
                factoryBean.setProperties(getProperties());
            }
            LOG.debug("ClientFactoryBean: {} added properties: {}", factoryBean, getProperties());
        }

        // setup the basic authentication property
        if (ObjectHelper.isNotEmpty(username)) {
            AuthorizationPolicy authPolicy = new AuthorizationPolicy();
            authPolicy.setUserName(username);
            authPolicy.setPassword(password);
            if (factoryBean.getProperties() == null) {
                factoryBean.setProperties(new HashMap<>());
            }
            factoryBean.getProperties().put(AuthorizationPolicy.class.getName(), authPolicy);
        }

        if (this.isSkipPayloadMessagePartCheck()) {
            if (factoryBean.getProperties() == null) {
                factoryBean.setProperties(new HashMap<>());
            }
            factoryBean.getProperties().put("soap.no.validate.parts", Boolean.TRUE);
        }

        if (this.isSkipFaultLogging()) {
            if (factoryBean.getProperties() == null) {
                factoryBean.setProperties(new HashMap<>());
            }
            factoryBean.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }

        if (this.getSchemaValidationEnabled() != null) {
            if (factoryBean.getProperties() == null) {
                factoryBean.setProperties(new HashMap<>());
            }
            factoryBean.getProperties().put(Message.SCHEMA_VALIDATION_ENABLED, schemaValidationEnabled);
        }

        factoryBean.setBus(getBus());

        getNullSafeCxfConfigurer().configure(factoryBean);
    }

    // Package private methods
    // -------------------------------------------------------------------------

    private void setWrapped(Object factoryBean, boolean wrapped) {
        try {
            Object sf = factoryBean.getClass().getMethod("getServiceFactory").invoke(factoryBean);
            sf.getClass().getMethod("setWrapped", Boolean.TYPE).invoke(sf, wrapped);
        } catch (Exception t) {
            throw new RuntimeException(t);
        }
    }

    private void setServiceFactory(Object factoryBean, Object serviceFactoryBean2) {
        for (Method m : factoryBean.getClass().getMethods()) {
            if ("setServiceFactory".equals(m.getName())
                    && m.getParameterTypes()[0].isInstance(serviceFactoryBean2)) {
                try {
                    ReflectionUtil.setAccessible(m).invoke(factoryBean, serviceFactoryBean2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Create a CXF client object
     */
    public Client createClient() throws Exception {

        // get service class
        if (getDataFormat().equals(DataFormat.POJO)) {
            ObjectHelper.notNull(getServiceClass(), CxfConstants.SERVICE_CLASS);
        }

        if (getWsdlURL() == null && getServiceClass() == null) {
            // no WSDL and serviceClass specified, set our default serviceClass
            setServiceClass(DefaultSEI.class.getName());
            setDefaultOperationNamespace(CxfConstants.DISPATCH_NAMESPACE);
            setDefaultOperationName(CxfConstants.DISPATCH_DEFAULT_OPERATION_NAMESPACE);
            if (getDataFormat().equals(DataFormat.PAYLOAD)) {
                setSkipPayloadMessagePartCheck(true);
            }
        }

        Class<?> cls = getServiceClass();
        ClientFactoryBean factoryBean;
        if (cls != null) {
            // create client factory bean
            factoryBean = createClientFactoryBean(cls);
        } else {
            factoryBean = createClientFactoryBean();
        }

        // setup client factory bean
        setupClientFactoryBean(factoryBean, cls);

        if (cls == null) {
            checkName(factoryBean.getEndpointName(), "endpoint/port name");
            checkName(factoryBean.getServiceName(), "service name");
        }

        Client client = factoryBean.create();

        // setup the handlers
        setupHandlers(factoryBean, client);
        return client;
    }

    public void checkName(Object value, String name) {
        if (ObjectHelper.isEmpty(value)) {
            LOG.warn("The {} of {} is empty, cxf will try to load the first one in wsdl for you.", name,
                    this.getEndpointUri());
        }
    }

    /**
     * Create a CXF server factory bean
     */
    protected ServerFactoryBean createServerFactoryBean() throws Exception {

        Class<?> cls = null;
        if (getDataFormat() == DataFormat.POJO) {
            ObjectHelper.notNull(getServiceClass(), CxfConstants.SERVICE_CLASS);
        }

        if (getWsdlURL() == null && getServiceClass() == null) {
            // no WSDL and serviceClass specified, set our default serviceClass
            if (getDataFormat().equals(DataFormat.PAYLOAD)) {
                setServiceClass(DefaultPayloadProviderSEI.class.getName());
            }
        }

        if (getServiceClass() != null) {
            cls = getServiceClass();
        }

        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;

        if (cls == null) {
            checkName(portName, " endpoint/port name");
            checkName(serviceName, " service name");
            answer = new JaxWsServerFactoryBean(new WSDLServiceFactoryBean());
            cls = Provider.class;
        } else if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            answer = new JaxWsServerFactoryBean();
        } else {
            answer = new ServerFactoryBean();
        }

        // setup server factory bean
        setupServerFactoryBean(answer, cls);
        return answer;
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

    // Properties
    // -------------------------------------------------------------------------

    public String getBeanId() {
        return beanId;
    }

    public void setBeanId(String beanId) {
        this.beanId = beanId;
    }

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(DataFormat format) {
        dataFormat = format;
    }

    public String getPublishedEndpointUrl() {
        return resolvePropertyPlaceholders(publishedEndpointUrl);
    }

    public void setPublishedEndpointUrl(String url) {
        publishedEndpointUrl = url;
    }

    public String getWsdlURL() {
        return resolvePropertyPlaceholders(wsdlURL);
    }

    /**
     * The location of the WSDL. Can be on the classpath, file system, or be hosted remotely.
     */
    public void setWsdlURL(String url) {
        wsdlURL = url;
    }

    public Class<?> getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(Class<?> cls) {
        serviceClass = cls;
    }

    public void setServiceClass(Object instance) {
        serviceClass = ClassHelper.getRealClass(instance);
    }

    public void setServiceClass(String type) throws ClassNotFoundException {
        if (ObjectHelper.isEmpty(type)) {
            throw new IllegalArgumentException("The serviceClass option can neither be null nor an empty String.");
        }
        serviceClass = ClassLoaderUtils.loadClass(resolvePropertyPlaceholders(type), getClass());
    }

    public void setServiceName(String service) {
        serviceName = service;
    }

    public String getServiceName() {
        return serviceName;
    }

    public QName getServiceNameAsQName() {
        if (serviceNameQName == null && serviceName != null) {
            serviceNameQName = QName.valueOf(resolvePropertyPlaceholders(serviceName));
        }
        //if not specify the service name and if the wsdlUrl is available,
        //parse the wsdl to see if only one service in it, if so set the only service
        //from wsdl to avoid ambiguity
        if (serviceNameQName == null && getWsdlURL() != null) {
            // use wsdl manager to parse wsdl or get cached
            // definition
            try {
                Definition definition = getBus().getExtension(WSDLManager.class)
                        .getDefinition(getWsdlURL());
                if (definition.getServices().size() == 1) {
                    serviceNameQName = (QName) definition.getServices().keySet()
                            .iterator().next();

                }
            } catch (WSDLException e) {
                throw new RuntimeException(e);
            }
        }
        return serviceNameQName;
    }

    public void setServiceNameAsQName(QName qName) {
        this.serviceNameQName = qName;
    }

    public QName getPortNameAsQName() {
        if (portNameQName == null && portName != null) {
            portNameQName = QName.valueOf(resolvePropertyPlaceholders(portName));
        }
        return portNameQName;
    }

    public void setPortNameAsQName(QName qName) {
        this.portNameQName = qName;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String port) {
        portName = port;
    }

    public void setEndpointName(String name) {
        // this is on purpose as camel-cxf in xml-dsl uses endpoint-name as port-name
        portName = name;
    }

    public void setEndpointNameAsQName(QName qName) {
        // this is on purpose as camel-cxf in xml-dsl uses endpoint-name as port-name
        portNameQName = qName;
    }

    public String getDefaultOperationName() {
        return resolvePropertyPlaceholders(defaultOperationName);
    }

    public void setDefaultOperationName(String name) {
        defaultOperationName = name;
    }

    public String getDefaultOperationNamespace() {
        return resolvePropertyPlaceholders(defaultOperationNamespace);
    }

    public void setDefaultOperationNamespace(String namespace) {
        defaultOperationNamespace = namespace;
    }

    public boolean isWrapped() {
        return wrapped;
    }

    public void setWrapped(boolean wrapped) {
        this.wrapped = wrapped;
    }

    public Boolean getWrappedStyle() {
        return wrappedStyle;
    }

    public void setWrappedStyle(Boolean wrapped) {
        wrappedStyle = wrapped;
    }

    public void setAllowStreaming(Boolean allowStreaming) {
        this.allowStreaming = allowStreaming;
    }

    public Boolean getAllowStreaming() {
        return allowStreaming;
    }

    /**
     * To use a custom CxfBinding to control the binding between Camel Message and CXF Message.
     */
    public void setCxfBinding(CxfBinding cxfBinding) {
        this.cxfBinding = cxfBinding;
    }

    public CxfBinding getCxfBinding() {
        return cxfBinding;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        if (cxfBinding instanceof HeaderFilterStrategyAware headerFilterStrategyAware) {
            headerFilterStrategyAware.setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom configured CXF Bus.
     */
    public void setBus(Bus bus) {
        this.bus = bus;
        this.createBus = false;
    }

    public Bus getBus() {
        if (bus == null) {
            CamelContext context = getCamelContext();
            if (context.getClass().getName().endsWith("SpringCamelContext") ||
                    context.getClass().getName().endsWith("SpringBootCamelContext")) {
                try {
                    Class<?> cxfSpringEndpintUtilClazz
                            = Class.forName("org.apache.camel.component.cxf.spring.jaxws.CxfSpringEndpointUtils");
                    Method method = cxfSpringEndpintUtilClazz.getMethod("createBus", CamelContext.class);
                    bus = (Bus) method.invoke(null, context);
                } catch (Exception ex) {
                    if (context.getClass().getName().endsWith("SpringCamelContext")) {
                        LOG.warn("Error creating Cxf Bus from SpringCamelContext: {} This exception will be ignored.",
                                ex.getMessage(), ex);
                    } else {
                        LOG.warn("Error creating Cxf Bus from SpringBootCamelContext: {} This exception will be ignored.",
                                ex.getMessage(), ex);
                    }
                    bus = CxfEndpointUtils.createBus();
                }
            } else {
                bus = CxfEndpointUtils.createBus();
            }
            this.createBus = true;
            LOG.debug("Using DefaultBus {}", bus);
        }

        if (!getBusHasBeenCalled.getAndSet(true) && defaultBus) {
            BusFactory.setDefaultBus(bus);
            LOG.debug("Set bus {} as thread default bus", bus);
        }
        return bus;
    }

    public void setDefaultBus(boolean defaultBus) {
        this.defaultBus = defaultBus;
    }

    public boolean isDefaultBus() {
        return defaultBus;
    }

    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        this.loggingFeatureEnabled = loggingFeatureEnabled;
    }

    public boolean isLoggingFeatureEnabled() {
        return loggingFeatureEnabled;
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

    public boolean isSkipPayloadMessagePartCheck() {
        return skipPayloadMessagePartCheck;
    }

    public void setSkipPayloadMessagePartCheck(boolean skipPayloadMessagePartCheck) {
        this.skipPayloadMessagePartCheck = skipPayloadMessagePartCheck;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public void setCamelContext(CamelContext c) {
        super.setCamelContext(c);
        if (this.properties != null) {
            try {
                PropertyBindingSupport.bindProperties(getCamelContext(),
                        this,
                        this.properties);
            } catch (Exception e) {
                // TODO: Why dont't we rethrown this exception
                LOG.warn("Error setting CamelContext. This exception will be ignored.", e);
            }
        }
    }

    /**
     * To set additional CXF options using the key/value pairs from the Map. For example to turn on stacktraces in SOAP
     * faults, <tt>properties.faultStackTraceEnabled=true</tt>
     */
    public void setProperties(Map<String, Object> properties) {
        if (this.properties == null) {
            this.properties = properties;
        } else {
            this.properties.putAll(properties);
        }
        if (getCamelContext() != null && this.properties != null) {
            try {
                PropertyBindingSupport.bindProperties(getCamelContext(),
                        this,
                        this.properties);
            } catch (Exception e) {
                // TODO: Why dont't we rethrown this exception
                LOG.warn("Error setting properties. This exception will be ignored.", e);
            }
        }
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

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (headerFilterStrategy == null) {
            headerFilterStrategy = new CxfHeaderFilterStrategy();
        }
        if (cxfBinding == null) {
            cxfBinding = new DefaultCxfBinding();
        }
        if (cxfBinding instanceof HeaderFilterStrategyAware headerFilterStrategyAware) {
            headerFilterStrategyAware.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // we should consider to shutdown the bus if the bus is created by cxfEndpoint
        if (createBus && bus != null) {
            LOG.debug("Shutdown CXF bus {}", bus);
            getBus().shutdown(false);
            // clean up the bus to create a new one if the endpoint is started again
            bus = null;
        }
    }

    public void setAddress(String address) {
        super.setEndpointUri(UnsafeUriCharactersEncoder.encodeHttpURI(address));
        this.address = address;
    }

    public String getAddress() {
        return resolvePropertyPlaceholders(address);
    }

    public void setMtomEnabled(boolean mtomEnabled) {
        this.mtomEnabled = mtomEnabled;
    }

    public boolean isMtomEnabled() {
        return mtomEnabled;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public CxfConfigurer getChainedCxfConfigurer() {
        return ChainedCxfConfigurer
                .create(getNullSafeCxfConfigurer(),
                        SslCxfConfigurer.create(sslContextParameters, getCamelContext()))
                .addChild(HostnameVerifierCxfConfigurer.create(hostnameVerifier));
    }

    private CxfConfigurer getNullSafeCxfConfigurer() {
        if (cxfConfigurer == null) {
            return new ChainedCxfConfigurer.NullCxfConfigurer();
        } else {
            return cxfConfigurer;
        }
    }

    /**
     * We need to override the {@link ClientImpl#setParameters} method to insert parameters into CXF Message for
     * {@link DataFormat#PAYLOAD} mode.
     */
    public class CamelCxfClientImpl extends ClientImpl {

        CamelCxfClientImpl(Bus bus, Endpoint ep) {
            super(bus, ep);
        }

        @Override
        protected Object[] processResult(
                Message message, org.apache.cxf.message.Exchange exchange,
                BindingOperationInfo oi, Map<String, Object> resContext)
                throws Exception {
            try {
                return super.processResult(message, exchange, oi, resContext);
            } catch (IllegalEmptyResponseException ex) {
                //Camel does not strickly enforce returning a value when a value is required from the WSDL/contract
                //Thus, we'll capture the exception raised and return a null
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void setParameters(Object[] params, Message message) {

            Object attachments = message.get(CxfConstants.CAMEL_CXF_ATTACHMENTS);
            if (attachments != null) {
                message.setAttachments((Collection<Attachment>) attachments);
                message.remove(CxfConstants.CAMEL_CXF_ATTACHMENTS);
            }

            // Don't try to reset the parameters if the parameter is not CxfPayload instance
            // as the setParameter will be called more than once when using the fail over feature
            if (DataFormat.PAYLOAD == message.get(DataFormat.class) && params[0] instanceof CxfPayload) {

                setParametersViaPayload(params, message);
            } else {
                super.setParameters(params, message);
            }

            message.remove(DataFormat.class.getName());
        }

        private void setParametersViaPayload(Object[] params, Message message) {
            CxfPayload<?> payload = (CxfPayload<?>) params[0];
            List<Source> elements = payload.getBodySources();

            BindingOperationInfo boi = message.get(BindingOperationInfo.class);
            MessageContentsList content = new MessageContentsList();
            int i = 0;

            for (MessagePartInfo partInfo : boi.getInput().getMessageParts()) {
                if (elements.size() > i) {
                    if (isSkipPayloadMessagePartCheck()) {
                        content.put(partInfo, elements.get(i++));
                    } else {
                        String name = findName(elements, i);
                        if (partInfo.getConcreteName().getLocalPart().equals(name)) {
                            content.put(partInfo, elements.get(i++));
                        }
                    }
                }
            }

            if (elements != null && content.size() < elements.size()) {
                throw new IllegalArgumentException(
                        "The PayLoad elements cannot fit with the message parts of the BindingOperation. Please check the BindingOperation and PayLoadMessage.");
            }

            message.setContent(List.class, content);
            // merge header list from request context with header list from CXF payload
            List<Object> headerListOfRequestContxt = (List<Object>) message.get(Header.HEADER_LIST);
            List<Object> headerListOfPayload = CastUtils.cast(payload.getHeaders());
            if (headerListOfRequestContxt == headerListOfPayload) {
                // == is correct, we want to compare the object instances
                // nothing to do, this can happen when the CXF payload is already created in the from-cxf-endpoint and then forwarded to a to-cxf-endpoint
            } else {
                if (headerListOfRequestContxt == null) {
                    message.put(Header.HEADER_LIST, payload.getHeaders());
                } else {
                    headerListOfRequestContxt.addAll(headerListOfPayload);
                }
            }
        }

        private String findName(List<Source> sources, int i) {
            Source source = sources.get(i);
            XMLStreamReader r = null;
            if (source instanceof DOMSource domSource) {
                Node nd = domSource.getNode();
                if (nd instanceof Document document) {
                    nd = document.getDocumentElement();
                }
                return nd.getLocalName();
            } else if (source instanceof StaxSource staxSource) {
                r = staxSource.getXMLStreamReader();
            } else if (source instanceof StAXSource stAXSource) {
                r = stAXSource.getXMLStreamReader();
            } else if (source instanceof StreamSource || source instanceof SAXSource) {
                //flip to stax so we can get the name
                r = StaxUtils.createXMLStreamReader(source);
                StaxSource src2 = new StaxSource(r);
                sources.set(i, src2);
            }
            if (r != null) {
                try {
                    if (r.getEventType() == XMLStreamConstants.START_DOCUMENT) {
                        r.next();
                    }
                    if (r.getEventType() != XMLStreamConstants.START_ELEMENT) {
                        r.nextTag();
                    }
                } catch (XMLStreamException e) {
                    //ignore
                    LOG.warn("Error finding the start element.", e);
                    return null;
                }
                return r.getLocalName();
            }
            return null;
        }
    }

    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return outFault;
    }

    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return inFault;
    }

    public List<Interceptor<? extends Message>> getInInterceptors() {
        return in;
    }

    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return out;
    }

    public void setInInterceptors(List<Interceptor<? extends Message>> interceptors) {
        in = interceptors;
    }

    public void setInFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        inFault = interceptors;
    }

    public void setOutInterceptors(List<Interceptor<? extends Message>> interceptors) {
        out = interceptors;
    }

    public void setOutFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        outFault = interceptors;
    }

    public void setFeatures(List<Feature> f) {
        features = f;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    @SuppressWarnings("rawtypes")
    public void setHandlers(List<Handler> h) {
        handlers = h;
    }

    @SuppressWarnings("rawtypes")
    public List<Handler> getHandlers() {
        return handlers;
    }

    public void setSchemaLocations(List<String> sc) {
        schemaLocations = sc;
    }

    public List<String> getSchemaLocations() {
        return schemaLocations;
    }

    public String getTransportId() {
        return resolvePropertyPlaceholders(transportId);
    }

    public void setTransportId(String transportId) {
        this.transportId = transportId;
    }

    public String getBindingId() {
        return resolvePropertyPlaceholders(bindingId);
    }

    public void setBindingId(String bindingId) {
        this.bindingId = bindingId;
    }

    public BindingConfiguration getBindingConfig() {
        return bindingConfig;
    }

    public boolean isSkipFaultLogging() {
        return skipFaultLogging;
    }

    public void setSkipFaultLogging(boolean skipFaultLogging) {
        this.skipFaultLogging = skipFaultLogging;
    }

    public boolean isMuteException() {
        return muteException;
    }

    public void setMuteException(boolean muteException) {
        this.muteException = muteException;
    }

    public boolean isMergeProtocolHeaders() {
        return mergeProtocolHeaders;
    }

    public void setMergeProtocolHeaders(boolean mergeProtocolHeaders) {
        this.mergeProtocolHeaders = mergeProtocolHeaders;
    }

    public void setBindingConfig(BindingConfiguration bindingConfig) {
        this.bindingConfig = bindingConfig;
    }

    public DataBinding getDataBinding() {
        return dataBinding;
    }

    public void setDataBinding(DataBinding dataBinding) {
        this.dataBinding = dataBinding;
    }

    public Object getServiceFactoryBean() {
        return serviceFactoryBean;
    }

    public void setServiceFactoryBean(Object serviceFactoryBean) {
        this.serviceFactoryBean = serviceFactoryBean;
    }

    public void setServiceFactory(Object serviceFactoryBean) {
        // needed a setter with this name as the cxf namespace parser expects this name
        this.serviceFactoryBean = serviceFactoryBean;
    }

    public CxfConfigurer getCxfConfigurer() {
        return cxfConfigurer;
    }

    public void setCxfConfigurer(CxfConfigurer configurer) {
        this.cxfConfigurer = configurer;
    }

    public long getContinuationTimeout() {
        return continuationTimeout;
    }

    public void setContinuationTimeout(long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
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

    /**
     * get the request uri for a given exchange.
     */
    URI getRequestUri(Exchange camelExchange) {
        String uriString = camelExchange.getIn().getHeader(CxfConstants.DESTINATION_OVERRIDE_URL, String.class);
        if (uriString == null) {
            uriString = getAddress();
        }
        try {
            return new URI(uriString);
        } catch (URISyntaxException e) {
            LOG.error("cannot determine request URI", e);
            return null;
        }
    }

    public Boolean getSchemaValidationEnabled() {
        return schemaValidationEnabled;
    }

    public void setSchemaValidationEnabled(Boolean schemaValidationEnabled) {
        this.schemaValidationEnabled = schemaValidationEnabled;
    }
}
