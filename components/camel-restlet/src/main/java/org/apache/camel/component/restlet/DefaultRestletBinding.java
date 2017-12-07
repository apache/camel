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
package org.apache.camel.component.restlet;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StringSource;
import org.apache.camel.TypeConverter;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.AuthenticationInfo;
import org.restlet.data.CacheDirective;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.CharacterSet;
import org.restlet.data.ClientInfo;
import org.restlet.data.Cookie;
import org.restlet.data.Encoding;
import org.restlet.data.Form;
import org.restlet.data.Header;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Preference;
import org.restlet.data.Status;
import org.restlet.engine.application.DecodeRepresentation;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.engine.header.HeaderUtils;
import org.restlet.representation.ByteArrayRepresentation;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.FileRepresentation;
import org.restlet.representation.InputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StreamRepresentation;
import org.restlet.representation.StringRepresentation;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default Restlet binding implementation
 */
public class DefaultRestletBinding implements RestletBinding, HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRestletBinding.class);
    private static final String RFC_2822_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss Z";
    private HeaderFilterStrategy headerFilterStrategy;
    private boolean streamRepresentation;
    private boolean autoCloseStream;

    public void populateExchangeFromRestletRequest(Request request, Response response, Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();

        inMessage.setHeader(RestletConstants.RESTLET_REQUEST, request);
        inMessage.setHeader(RestletConstants.RESTLET_RESPONSE, response);

        // extract headers from restlet
        for (Map.Entry<String, Object> entry : request.getAttributes().entrySet()) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (HeaderConstants.ATTRIBUTE_HEADERS.equalsIgnoreCase(key)) {
                    Series<Header> series = (Series<Header>) value;
                    for (Header header: series) {
                        if (!headerFilterStrategy.applyFilterToExternalHeaders(header.getName(), header.getValue(), exchange)) {
                            inMessage.setHeader(header.getName(), header.getValue());
                        }
                    }
                } else {
                    inMessage.setHeader(key, value);
                }
                LOG.debug("Populate exchange from Restlet request header: {} value: {}", key, value);
            }
        }


        // copy query string to header
        populateQueryParameters(request, exchange);

        // copy URI to header
        inMessage.setHeader(Exchange.HTTP_URI, request.getResourceRef().getIdentifier(true));

        // copy HTTP method to header
        inMessage.setHeader(Exchange.HTTP_METHOD, request.getMethod().toString());

        if (!request.isEntityAvailable()) {
            return;
        }

        // only deal with the form if the content type is "application/x-www-form-urlencoded"
        if (request.getEntity().getMediaType() != null && request.getEntity().getMediaType().equals(MediaType.APPLICATION_WWW_FORM, true)) {
            Form form = new Form(request.getEntity());
            for (String paramName : form.getValuesMap().keySet()) {
                String[] values = form.getValuesArray(paramName);
                Object value = null;
                if (values != null && values.length > 0) {
                    if (values.length == 1) {
                        value = values[0];
                    } else {
                        value = values;
                    }
                }
                if (value == null) {
                    inMessage.setBody(paramName);
                    LOG.debug("Populate exchange from Restlet request body: {}", paramName);
                } else {
                    if (!headerFilterStrategy.applyFilterToExternalHeaders(paramName, value, exchange)) {
                        inMessage.setHeader(paramName, value);
                        LOG.debug("Populate exchange from Restlet request user header: {} value: {}", paramName, value);
                    }
                }
            }
        } else {
            InputStream is = request.getEntity().getStream();
            Object body = RestletHelper.readResponseBodyFromInputStream(is, exchange);
            inMessage.setBody(body);
        }

    }

    protected void populateQueryParameters(Request request, Exchange exchange) throws Exception {
        String query = request.getResourceRef().getQuery();
        if (query != null) {
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, query);

            // parse query and map to Camel message headers
            Map<String, Object> map = URISupport.parseQuery(query);
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    LOG.trace("HTTP query parameter {} = {}", key, value);
                    exchange.getIn().setHeader(key, value);
                }
            }
        }
    }

    public void populateRestletRequestFromExchange(Request request, Exchange exchange) {
        request.setReferrerRef("camel-restlet");

        final Method method = request.getMethod();

        MediaType mediaType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, MediaType.class);
        if (mediaType == null) {
            mediaType = MediaType.APPLICATION_WWW_FORM;
        }

        Form form = null;
        // Use forms only for PUT, POST and x-www-form-urlencoded
        if ((Method.PUT == method || Method.POST == method) && MediaType.APPLICATION_WWW_FORM.equals(mediaType, true)) {
            form = new Form();
            
            if (exchange.getIn().getBody() instanceof Map) {
                //Body is key value pairs
                try {
                    Map pairs = exchange.getIn().getBody(Map.class);
                    for (Object key: pairs.keySet()) {
                        Object value = pairs.get(key);
                        form.add(key.toString(), value != null ? value.toString() : null);
                    }
                } catch (Exception e) {
                    throw new RuntimeCamelException("body for " + MediaType.APPLICATION_WWW_FORM + " request must be Map<String,String> or string format like name=bob&password=secRet", e);
                }
            } else {
                // use string based for forms
                String body = exchange.getIn().getBody(String.class);
                if (body != null) {
                    List<NameValuePair> pairs = URLEncodedUtils.parse(body, Charset.forName(IOHelper.getCharsetName(exchange, true)));
                    for (NameValuePair p : pairs) {
                        form.add(p.getName(), p.getValue());
                    }
                }
            }
        }

        // get outgoing custom http headers from the exchange if they exists
        Series<Header> restletHeaders = exchange.getIn().getHeader(HeaderConstants.ATTRIBUTE_HEADERS, Series.class);
        if (restletHeaders == null) {
            restletHeaders = new Series<Header>(Header.class);
            request.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, restletHeaders);
        } else {
            // if the restlet headers already exists on the exchange, we need to filter them
            for (String name : restletHeaders.getNames()) {
                if (headerFilterStrategy.applyFilterToCamelHeaders(name, restletHeaders.getValues(name), exchange)) {
                    restletHeaders.removeAll(name);
                }
            }
            request.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, restletHeaders);

            // since the restlet headers already exists remove them from the exchange so they don't get added again below
            // we will get a new set of restlet headers on the response
            exchange.getIn().removeHeader(HeaderConstants.ATTRIBUTE_HEADERS);
        }

        // login and password are filtered by header filter strategy
        String login = exchange.getIn().getHeader(RestletConstants.RESTLET_LOGIN, String.class);
        String password = exchange.getIn().getHeader(RestletConstants.RESTLET_PASSWORD, String.class);

        if (login != null && password != null) {
            ChallengeResponse authentication = new ChallengeResponse(ChallengeScheme.HTTP_BASIC, login, password);
            request.setChallengeResponse(authentication);
            LOG.debug("Basic HTTP Authentication has been applied");
        }

        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange)) {
                // Use forms only for PUT, POST and x-www-form-urlencoded
                if (form != null) {
                    if (key.startsWith("org.restlet.")) {
                        // put the org.restlet headers in attributes
                        request.getAttributes().put(key, value);
                    } else {
                        // put the user stuff in the form
                        if (value instanceof Collection) {
                            for (Object v : (Collection<?>) value) {
                                form.add(key, v.toString());
                                if (!headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange)) {
                                    restletHeaders.set(key, value.toString());
                                }
                            }
                        } else {
                            //Add headers to headers and to body
                            form.add(key, value.toString());
                            if (!headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange)) {
                                restletHeaders.set(key, value.toString());
                            }
                        }
                    }
                } else {
                    // For non-form post put all the headers in custom headers
                    if (!headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange)) {
                        restletHeaders.set(key, value.toString());
                    }
                }
                LOG.debug("Populate Restlet request from exchange header: {} value: {}", key, value);
            }
        }

        if (form != null) {
            request.setEntity(form.getWebRepresentation());
            LOG.debug("Populate Restlet {} request from exchange body as form using media type {}", method, mediaType);
        } else {
            // include body if PUT or POST
            if (request.getMethod() == Method.PUT || request.getMethod() == Method.POST) {
                Representation body = createRepresentationFromBody(exchange, mediaType);
                request.setEntity(body);
                LOG.debug("Populate Restlet {} request from exchange body: {} using media type {}", method, body, mediaType);
            } else {
                // no body
                LOG.debug("Populate Restlet {} request from exchange using media type {}", method, mediaType);
                request.setEntity(new EmptyRepresentation());
            }
        }

        // filter out standard restlet headers which must be configured differently
        org.restlet.Message extensionHeaders = new Request();
        HeaderUtils.copyExtensionHeaders(restletHeaders, extensionHeaders);

        // setup standard headers
        Series<Header> standardHeaders = new Series<>(Header.class);
        standardHeaders.addAll(restletHeaders);
        standardHeaders.removeAll(extensionHeaders.getHeaders());

        // setup extension headers
        restletHeaders.removeAll(standardHeaders);

        // now add standard headers but via the special restlet api
        LOG.debug("Detected {} request extension headers", extensionHeaders.getHeaders().size());
        LOG.debug("Detected {} request standard headers", standardHeaders.size());

        configureRestletRequestStandardHeaders(exchange, request, standardHeaders);

        // deprecated accept
        final MediaType[] acceptedMediaTypes = exchange.getIn().getHeader(Exchange.ACCEPT_CONTENT_TYPE, MediaType[].class);
        if (acceptedMediaTypes != null) {
            ClientInfo clientInfo = request.getClientInfo();
            List<Preference<MediaType>> acceptedMediaTypesList = clientInfo.getAcceptedMediaTypes();
            for (MediaType acceptedMediaType : acceptedMediaTypes) {
                acceptedMediaTypesList.add(new Preference<MediaType>(acceptedMediaType));
            }
        }
    }

    private void configureRestletRequestStandardHeaders(Exchange exchange, Request request, Series standardHeaders) {
        Iterator it = standardHeaders.iterator();
        while (it.hasNext()) {
            Header h = (Header) it.next();
            String key = h.getName();
            String value = h.getValue();

            // ignore these headers
            if ("Server".equalsIgnoreCase(key) || "Set-Cookie".equalsIgnoreCase(key) || "Expires".equalsIgnoreCase(key)
                || "Connection".equalsIgnoreCase(key)) {
                continue;
            }

            if ("Authorization".equalsIgnoreCase(key)) {
                // special workaround for restlet (https://github.com/restlet/restlet-framework-java/issues/1086)
                ChallengeResponse c = new ChallengeResponse(new ChallengeScheme("", ""));
                c.setRawValue(value);
                request.setChallengeResponse(c);
            } else if ("Accept".equalsIgnoreCase(key)) {
                ClientInfo clientInfo = request.getClientInfo();
                List<Preference<MediaType>> acceptedMediaTypesList = clientInfo.getAcceptedMediaTypes();
                MediaType[] acceptedMediaTypes = exchange.getContext().getTypeConverter().tryConvertTo(MediaType[].class, exchange, value);
                for (MediaType acceptedMediaType : acceptedMediaTypes) {
                    acceptedMediaTypesList.add(new Preference<MediaType>(acceptedMediaType));
                }
            } else if ("Accept-Encoding".equalsIgnoreCase(key)) {
                // assume only accepting one encoding
                ClientInfo clientInfo = request.getClientInfo();
                Encoding encoding = Encoding.valueOf(value);
                clientInfo.getAcceptedEncodings().add(new Preference<>(encoding));
            } else if ("Accept-Language".equalsIgnoreCase(key)) {
                // assume only accepting one encoding
                ClientInfo clientInfo = request.getClientInfo();
                Language language = Language.valueOf(value);
                clientInfo.getAcceptedLanguages().add(new Preference<>(language));
            } else if ("Cookie".equalsIgnoreCase(key)) {
                String k = StringHelper.before(value, "=");
                String v = StringHelper.after(value, "=");
                if (k != null && v != null) {
                    Cookie cookie = new Cookie(k, v);
                    request.getCookies().add(cookie);
                }
            } else if ("Content-Type".equalsIgnoreCase(key)) {
                MediaType mediaType = exchange.getContext().getTypeConverter().tryConvertTo(MediaType.class, exchange, value);
                if (mediaType != null) {
                    request.getEntity().setMediaType(mediaType);
                }
            } else if ("User-Agent".equalsIgnoreCase(key)) {
                request.getClientInfo().setAgent(value);
            } else if ("Referer".equalsIgnoreCase(key)) {
                request.setReferrerRef(value);
            } else if ("Host".equalsIgnoreCase(key)) {
                request.setHostRef(value);
            } else if ("Date".equalsIgnoreCase(key)) {
                Date d = exchange.getContext().getTypeConverter().tryConvertTo(Date.class, exchange, value);
                if (d != null) {
                    request.setDate(d);
                }
            } else {
                // TODO: implement all the other restlet standard headers
                LOG.warn("Addition of the standard request header \"{}\" is not allowed. Please use the equivalent property in the Restlet API.", key);
            }
        }
    }

    private void configureRestletResponseStandardHeaders(Exchange exchange, Response response, Series standardHeaders) {
        Iterator it = standardHeaders.iterator();
        while (it.hasNext()) {
            Header h = (Header) it.next();
            String key = h.getName();
            String value = h.getValue();


            // ignore these headers
            if ("Host".equalsIgnoreCase(key) || "Accept".equalsIgnoreCase(key) || "Accept-encoding".equalsIgnoreCase(key)
                || "User-Agent".equalsIgnoreCase(key) || "Referer".equalsIgnoreCase(key) || "Connection".equalsIgnoreCase(key)
                || "Cookie".equalsIgnoreCase(key)) {
                continue;
            }
            if ("Content-Type".equalsIgnoreCase(key)) {
                MediaType mediaType = exchange.getContext().getTypeConverter().tryConvertTo(MediaType.class, exchange, value);
                if (mediaType != null) {
                    response.getEntity().setMediaType(mediaType);
                }
            } else if ("Server".equalsIgnoreCase(key)) {
                response.getServerInfo().setAgent(value);
            } else if ("Age".equalsIgnoreCase(key)) {
                Integer age = exchange.getContext().getTypeConverter().tryConvertTo(Integer.class, exchange, value);
                if (age != null) {
                    response.setAge(age);
                }
            } else if ("Expires".equalsIgnoreCase(key)) {
                Date date = exchange.getContext().getTypeConverter().tryConvertTo(Date.class, exchange, value);
                if (date != null) {
                    response.getEntity().setExpirationDate(date);
                }
            } else if ("Date".equalsIgnoreCase(key)) {
                Date d = exchange.getContext().getTypeConverter().tryConvertTo(Date.class, exchange, value);
                if (d != null) {
                    response.setDate(d);
                }
            } else if ("Access-Control-Max-Age".equalsIgnoreCase(key)) {
                Integer accessControlMaxAge = exchange.getContext().getTypeConverter().tryConvertTo(Integer.class, exchange, value);
                if (accessControlMaxAge != null) {
                    response.setAccessControlMaxAge(accessControlMaxAge);
                }
            } else {
                // TODO: implement all the other restlet standard headers
                LOG.warn("Addition of the standard response header \"{}\" is not allowed. Please use the equivalent property in the Restlet API.", key);
            }
        }
    }

    public void populateRestletResponseFromExchange(Exchange exchange, Response response) throws Exception {
        Message out;
        if (exchange.isFailed()) {
            // 500 for internal server error which can be overridden by response code in header
            response.setStatus(Status.valueOf(500));
            Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
            if (msg.isFault()) {
                out = msg;
            } else {
                // print exception as message and stacktrace
                Exception t = exchange.getException();
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                response.setEntity(sw.toString(), MediaType.TEXT_PLAIN);
                return;
            }
        } else {
            out = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        }

        // get content type
        MediaType mediaType = out.getHeader(Exchange.CONTENT_TYPE, MediaType.class);
        if (mediaType == null) {
            Object body = out.getBody();
            mediaType = MediaType.TEXT_PLAIN;
            if (body instanceof String) {
                mediaType = MediaType.TEXT_PLAIN;
            } else if (body instanceof StringSource || body instanceof DOMSource) {
                mediaType = MediaType.TEXT_XML;
            }
        }

        // get response code
        Integer responseCode = out.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (responseCode != null) {
            response.setStatus(Status.valueOf(responseCode));
        }

        // set response body according to the message body
        Object body = out.getBody();
        if (body instanceof WrappedFile) {
            // grab body from generic file holder
            GenericFile<?> gf = (GenericFile<?>) body;
            body = gf.getBody();
        }

        if (body == null) {
            // empty response
            response.setEntity("", MediaType.TEXT_PLAIN);
            // if empty response and status is OK, then set it to NO_CONTENT which is more correct
            if (Status.SUCCESS_OK.equals(response.getStatus())) {
                response.setStatus(Status.SUCCESS_NO_CONTENT);
            }
        } else if (body instanceof Response) {
            // its already a restlet response, so dont do anything
            LOG.debug("Using existing Restlet Response from exchange body: {}", body);
        } else if (body instanceof Representation) {
            response.setEntity(out.getBody(Representation.class));
        } else if (body instanceof InputStream) {
            response.setEntity(new InputRepresentation(out.getBody(InputStream.class), mediaType));
        } else if (body instanceof File) {
            response.setEntity(new FileRepresentation(out.getBody(File.class), mediaType));
        } else if (body instanceof byte[]) {
            byte[] bytes = out.getBody(byte[].class);
            response.setEntity(new ByteArrayRepresentation(bytes, mediaType, bytes.length));
        } else {
            // fallback and use string
            String text = out.getBody(String.class);
            response.setEntity(text, mediaType);
        }
        LOG.debug("Populate Restlet response from exchange body: {}", body);

        if (exchange.getProperty(Exchange.CHARSET_NAME) != null) {
            CharacterSet cs = CharacterSet.valueOf(exchange.getProperty(Exchange.CHARSET_NAME, String.class));
            response.getEntity().setCharacterSet(cs);
        }

        // set headers at the end, as the entity must be set first
        // NOTE: setting HTTP headers on restlet is cumbersome and its API is "weird" and has some flaws
        // so we need to headers two times, and the 2nd time we add the non-internal headers once more
        Series<Header> series = new Series<Header>(Header.class);
        for (Map.Entry<String, Object> entry : out.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange)) {
                boolean added = setResponseHeader(exchange, response, key, value);
                if (!added) {
                    // we only want non internal headers
                    if (!key.startsWith("Camel") && !key.startsWith("org.restlet")) {
                        String text = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, value);
                        if (text != null) {
                            series.add(key, text);
                        }
                    }
                }
            }
        }

        // filter out standard restlet headers which must be configured differently
        org.restlet.Message extensionHeaders = new Request();
        HeaderUtils.copyExtensionHeaders(series, extensionHeaders);

        // setup standard headers
        Series<Header> standardHeaders = new Series<>(Header.class);
        standardHeaders.addAll(series);
        standardHeaders.removeAll(extensionHeaders.getHeaders());

        // setup extension headers
        series.removeAll(standardHeaders);

        // now add standard headers but via the special restlet api
        LOG.debug("Detected {} response extension headers", extensionHeaders.getHeaders().size());
        LOG.debug("Detected {} response standard headers", standardHeaders.size());

        configureRestletResponseStandardHeaders(exchange, response, standardHeaders);

        // include the extension headers on the response
        if (extensionHeaders.getHeaders().size() > 0) {
            response.getHeaders().addAll(extensionHeaders.getHeaders());
        }
    }

    public void populateExchangeFromRestletResponse(Exchange exchange, Response response) throws Exception {
        for (Map.Entry<String, Object> entry : response.getAttributes().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!headerFilterStrategy.applyFilterToExternalHeaders(key, value, exchange)) {
                exchange.getOut().setHeader(key, value);
                LOG.debug("Populate exchange from Restlet response header: {} value: {}", key, value);
            }
        }

        // set response code
        int responseCode = response.getStatus().getCode();
        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);

        // set restlet response as header so end user have access to it if needed
        exchange.getOut().setHeader(RestletConstants.RESTLET_RESPONSE, response);

        if (response.getEntity() != null) {
            // get content type
            MediaType mediaType = response.getEntity().getMediaType();
            if (mediaType != null) {
                LOG.debug("Setting the Content-Type to be {}",  mediaType.toString());
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, mediaType.toString());
            }
            if (streamRepresentation && response.getEntity() instanceof StreamRepresentation) {
                Representation representationDecoded = new DecodeRepresentation(response.getEntity());
                InputStream is = representationDecoded.getStream();
                exchange.getOut().setBody(is);
                if (autoCloseStream) {
                    // ensure the input stream is closed when we are done routing
                    exchange.addOnCompletion(new RestletOnCompletion(is));
                }
            } else if (response.getEntity() instanceof Representation) {
                Representation representationDecoded = new DecodeRepresentation(response.getEntity());
                exchange.getOut().setBody(representationDecoded.getText());
            } else {
                // get content text by default
                String text = response.getEntity().getText();
                LOG.debug("Populate exchange from Restlet response: {}", text);
                exchange.getOut().setBody(text);
            }
        }

        // preserve headers from in by copying any non existing headers
        // to avoid overriding existing headers with old values
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), false);
    }

    @SuppressWarnings("unchecked")
    protected boolean setResponseHeader(Exchange exchange, org.restlet.Response message, String header, Object value) {
        // there must be a value going forward
        if (value == null) {
            return true;
        }

        // must put to attributes
        message.getAttributes().put(header, value);

        // special for certain headers
        if (message.getEntity() != null) {
            // arfg darn restlet you make using your api harder for end users with all this trick just to set those ACL headers
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS)) {
                Boolean bool = exchange.getContext().getTypeConverter().tryConvertTo(Boolean.class, value);
                if (bool != null) {
                    message.setAccessControlAllowCredentials(bool);
                }
                return true;
            }
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_ACCESS_CONTROL_ALLOW_HEADERS)) {
                Set<String> set = convertToStringSet(value, exchange.getContext().getTypeConverter());
                message.setAccessControlAllowHeaders(set);
                return true;
            }
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_ACCESS_CONTROL_ALLOW_METHODS)) {
                Set<Method> set = convertToMethodSet(value, exchange.getContext().getTypeConverter());
                message.setAccessControlAllowMethods(set);
                return true;
            }
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN)) {
                String text = exchange.getContext().getTypeConverter().tryConvertTo(String.class, value);
                if (text != null) {
                    message.setAccessControlAllowOrigin(text);
                }
                return true;
            }
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS)) {
                Set<String> set = convertToStringSet(value, exchange.getContext().getTypeConverter());
                message.setAccessControlExposeHeaders(set);
                return true;
            }
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_CACHE_CONTROL)) {
                if (value instanceof List) {
                    message.setCacheDirectives((List<CacheDirective>) value);
                }
                if (value instanceof String) {
                    List<CacheDirective> list = new ArrayList<CacheDirective>();
                    // set the cache control value directive
                    list.add(new CacheDirective((String) value));
                    message.setCacheDirectives(list);
                }
                return true;
            }
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_LOCATION)) {
                String text = exchange.getContext().getTypeConverter().tryConvertTo(String.class, value);
                if (text != null) {
                    message.setLocationRef(text);
                }
                return true;
            }
            if (header.equalsIgnoreCase(HeaderConstants.HEADER_EXPIRES)) {
                if (value instanceof Calendar) {
                    message.getEntity().setExpirationDate(((Calendar) value).getTime());
                } else if (value instanceof Date) {
                    message.getEntity().setExpirationDate((Date) value);
                } else if (value instanceof String) {
                    SimpleDateFormat format = new SimpleDateFormat(RFC_2822_DATE_PATTERN, Locale.ENGLISH);
                    try {
                        Date date = format.parse((String) value);
                        message.getEntity().setExpirationDate(date);
                    } catch (ParseException e) {
                        LOG.debug("Header {} with value {} cannot be converted as a Date. The value will be ignored.", HeaderConstants.HEADER_EXPIRES, value);
                    }
                }
                return true;
            }

            if (header.equalsIgnoreCase(HeaderConstants.HEADER_LAST_MODIFIED)) {
                if (value instanceof Calendar) {
                    message.getEntity().setModificationDate(((Calendar) value).getTime());
                } else if (value instanceof Date) {
                    message.getEntity().setModificationDate((Date) value);
                } else if (value instanceof String) {
                    SimpleDateFormat format = new SimpleDateFormat(RFC_2822_DATE_PATTERN, Locale.ENGLISH);
                    try {
                        Date date = format.parse((String) value);
                        message.getEntity().setModificationDate(date);
                    } catch (ParseException e) {
                        LOG.debug("Header {} with value {} cannot be converted as a Date. The value will be ignored.", HeaderConstants.HEADER_LAST_MODIFIED, value);
                    }
                }
                return true;
            }

            if (header.equalsIgnoreCase(HeaderConstants.HEADER_CONTENT_LENGTH)) {
                if (value instanceof Long) {
                    message.getEntity().setSize((Long) value);
                } else if (value instanceof Integer) {
                    message.getEntity().setSize((Integer) value);
                } else {
                    Long num = exchange.getContext().getTypeConverter().tryConvertTo(Long.class, value);
                    if (num != null) {
                        message.getEntity().setSize(num);
                    } else {
                        LOG.debug("Header {} with value {} cannot be converted as a Long. The value will be ignored.", HeaderConstants.HEADER_CONTENT_LENGTH, value);
                    }
                }
                return true;
            }

            if (header.equalsIgnoreCase(HeaderConstants.HEADER_CONTENT_TYPE)) {
                if (value instanceof MediaType) {
                    message.getEntity().setMediaType((MediaType) value);
                } else {
                    String type = value.toString();
                    MediaType media = MediaType.valueOf(type);
                    if (media != null) {
                        message.getEntity().setMediaType(media);
                    } else {
                        LOG.debug("Header {} with value {} cannot be converted as a MediaType. The value will be ignored.", HeaderConstants.HEADER_CONTENT_TYPE, value);
                    }
                }
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private Set<String> convertToStringSet(Object value, TypeConverter typeConverter) {
        if (value instanceof Set) {
            return (Set<String>) value;
        }
        Set<String> set = new LinkedHashSet<>();
        Iterator it = ObjectHelper.createIterator(value);
        while (it.hasNext()) {
            Object next = it.next();
            String text = typeConverter.tryConvertTo(String.class, next);
            if (text != null) {
                set.add(text.trim());
            }
        }
        return set;
    }

    @SuppressWarnings("unchecked")
    private Set<Method> convertToMethodSet(Object value, TypeConverter typeConverter) {
        if (value instanceof Set) {
            return (Set<Method>) value;
        }
        Set<Method> set = new LinkedHashSet<>();
        Iterator it = ObjectHelper.createIterator(value);
        while (it.hasNext()) {
            Object next = it.next();
            String text = typeConverter.tryConvertTo(String.class, next);
            if (text != null) {
                Method method = Method.valueOf(text.trim()); // creates new instance only if no matching instance exists
                set.add(method);
            }
        }
        return set;
    }

    protected Representation createRepresentationFromBody(Exchange exchange, MediaType mediaType) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return new EmptyRepresentation();
        }

        // unwrap file
        if (body instanceof WrappedFile) {
            body = ((WrappedFile) body).getFile();
        }

        if (body instanceof InputStream) {
            return new InputRepresentation((InputStream) body, mediaType);
        } else if (body instanceof File) {
            return new FileRepresentation((File) body, mediaType);
        } else if (body instanceof byte[]) {
            return new ByteArrayRepresentation((byte[]) body, mediaType);
        } else if (body instanceof String) {
            return new StringRepresentation((CharSequence) body, mediaType);
        }

        // fallback as string
        body = exchange.getIn().getBody(String.class);
        if (body != null) {
            return new StringRepresentation((CharSequence) body, mediaType);
        } else {
            return new EmptyRepresentation();
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }

    public boolean isStreamRepresentation() {
        return streamRepresentation;
    }

    public void setStreamRepresentation(boolean streamRepresentation) {
        this.streamRepresentation = streamRepresentation;
    }

    public boolean isAutoCloseStream() {
        return autoCloseStream;
    }

    public void setAutoCloseStream(boolean autoCloseStream) {
        this.autoCloseStream = autoCloseStream;
    }
}
