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
package org.apache.camel.component.netty.http;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.NettyConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;

/**
 * Default {@link NettyHttpBinding}.
 */
public class DefaultNettyHttpBinding implements NettyHttpBinding, Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNettyHttpBinding.class);
    private HeaderFilterStrategy headerFilterStrategy = new NettyHttpHeaderFilterStrategy();

    public DefaultNettyHttpBinding() {
    }

    public DefaultNettyHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public DefaultNettyHttpBinding copy() {
        try {
            return (DefaultNettyHttpBinding)this.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public Message toCamelMessage(FullHttpRequest request, Exchange exchange, NettyHttpConfiguration configuration) throws Exception {
        LOG.trace("toCamelMessage: {}", request);

        NettyHttpMessage answer = new NettyHttpMessage(exchange.getContext(), request, null);
        answer.setExchange(exchange);
        if (configuration.isMapHeaders()) {
            populateCamelHeaders(request, answer.getHeaders(), exchange, configuration);
        }

        if (configuration.isHttpProxy() || configuration.isDisableStreamCache()) {
            // keep the body as is, and use type converters
            // for proxy use case pass the request body buffer directly to the response to avoid additional processing
            answer.setBody(request.content());
        } else {
            // turn the body into stream cached (on the client/consumer side we can facade the netty stream instead of converting to byte array)
            NettyChannelBufferStreamCache cache = new NettyChannelBufferStreamCache(request.content());
            // add on completion to the cache which is needed for Camel to keep track of the lifecycle of the cache
            exchange.addOnCompletion(new NettyChannelBufferStreamCacheOnCompletion(cache));
            answer.setBody(cache);
        }
        return answer;
    }

    @Override
    public void populateCamelHeaders(FullHttpRequest request, Map<String, Object> headers, Exchange exchange, NettyHttpConfiguration configuration) throws Exception {
        LOG.trace("populateCamelHeaders: {}", request);

        // NOTE: these headers is applied using the same logic as camel-http/camel-jetty to be consistent

        headers.put(Exchange.HTTP_METHOD, request.method().name());
        // strip query parameters from the uri
        String s = request.uri();
        if (s.contains("?")) {
            s = StringHelper.before(s, "?");
        }

        // we want the full path for the url, as the client may provide the url in the HTTP headers as absolute or relative, eg
        //   /foo
        //   http://servername/foo
        String http = configuration.isSsl() ? "https://" : "http://";
        if (!s.startsWith(http)) {
            if (configuration.getPort() != 80) {
                s = http + configuration.getHost() + ":" + configuration.getPort() + s;
            } else {
                s = http + configuration.getHost() + s;
            }
        }

        headers.put(Exchange.HTTP_URL, s);
        // uri is without the host and port
        URI uri = new URI(request.uri());
        // uri is path and query parameters
        headers.put(Exchange.HTTP_URI, uri.getPath());
        headers.put(Exchange.HTTP_QUERY, uri.getQuery());
        headers.put(Exchange.HTTP_RAW_QUERY, uri.getRawQuery());
        headers.put(Exchange.HTTP_SCHEME, uri.getScheme());
        headers.put(Exchange.HTTP_HOST, uri.getHost());
        final int port = uri.getPort();
        headers.put(Exchange.HTTP_PORT, port > 0 ? port : 80);

        // strip the starting endpoint path so the path is relative to the endpoint uri
        String path = uri.getRawPath();
        if (configuration.getPath() != null) {
            // need to match by lower case as we want to ignore case on context-path
            String matchPath = path.toLowerCase(Locale.US);
            String match = configuration.getPath() != null ? configuration.getPath().toLowerCase(Locale.US) : null;
            if (match != null && matchPath.startsWith(match)) {
                path = path.substring(configuration.getPath().length());
            }
        }
        // keep the path uri using the case the request provided (do not convert to lower case)
        headers.put(Exchange.HTTP_PATH, path);

        if (LOG.isTraceEnabled()) {
            LOG.trace("HTTP-Method {}", request.method().name());
            LOG.trace("HTTP-Uri {}", request.uri());
        }

        for (String name : request.headers().names()) {
            // mapping the content-type
            if (name.toLowerCase(Locale.US).equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }

            if (name.toLowerCase(Locale.US).equals("authorization")) {
                String value = request.headers().get(name);
                // store a special header that this request was authenticated using HTTP Basic
                if (value != null && value.trim().startsWith("Basic")) {
                    NettyHttpHelper.appendHeader(headers, NettyHttpConstants.HTTP_AUTHENTICATION, "Basic");
                }
            }

            // add the headers one by one, and use the header filter strategy
            List<String> values = request.headers().getAll(name);
            Iterator<?> it = ObjectHelper.createIterator(values, ",", true);
            while (it.hasNext()) {
                Object extracted = it.next();
                Object decoded = shouldUrlDecodeHeader(configuration, name, extracted, "UTF-8");
                LOG.trace("HTTP-header: {}", extracted);
                if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(name, decoded, exchange)) {
                    NettyHttpHelper.appendHeader(headers, name, decoded);
                }
            }
        }

        // add uri parameters as headers to the Camel message
        if (request.uri().contains("?")) {
            String query = StringHelper.after(request.uri(), "?");
            Map<String, Object> uriParameters = URISupport.parseQuery(query, false, true);

            for (Map.Entry<String, Object> entry : uriParameters.entrySet()) {
                String name = entry.getKey();
                Object values = entry.getValue();
                Iterator<?> it = ObjectHelper.createIterator(values, ",", true);
                while (it.hasNext()) {
                    Object extracted = it.next();
                    Object decoded = shouldUrlDecodeHeader(configuration, name, extracted, "UTF-8");
                    LOG.trace("URI-Parameter: {}", extracted);
                    if (headerFilterStrategy != null
                            && !headerFilterStrategy.applyFilterToExternalHeaders(name, decoded, exchange)) {
                        NettyHttpHelper.appendHeader(headers, name, decoded);
                    }
                }
            }
        }

        // if body is application/x-www-form-urlencoded then extract the body as query string and append as headers
        // if it is a bridgeEndpoint we need to skip this part of work
        if (request.method().name().equals("POST") && request.headers().get(Exchange.CONTENT_TYPE) != null
                && request.headers().get(Exchange.CONTENT_TYPE).startsWith(NettyHttpConstants.CONTENT_TYPE_WWW_FORM_URLENCODED)
                && !configuration.isBridgeEndpoint()) {

            String charset = "UTF-8";

            // Push POST form params into the headers to retain compatibility with DefaultHttpBinding
            String body;
            ByteBuf buffer = request.content();
            try {
                body = buffer.toString(Charset.forName(charset));
            } finally {
                buffer.release();
            }
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(body)) {
                for (String param : body.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2) {
                        String name = shouldUrlDecodeHeader(configuration, "", pair[0], charset);
                        String value = shouldUrlDecodeHeader(configuration, name, pair[1], charset);
                        if (headerFilterStrategy != null
                                && !headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                            NettyHttpHelper.appendHeader(headers, name, value);
                        }
                    } else {
                        throw new IllegalArgumentException("Invalid parameter, expected to be a pair but was " + param);
                    }
                }
            }
        }

    }

    /**
     * Decodes the header if needed to, or returns the header value as is.
     *
     * @param configuration  the configuration
     * @param headerName     the header name
     * @param value          the current header value
     * @param charset        the charset to use for decoding
     * @return  the decoded value (if decoded was needed) or a <tt>toString</tt> representation of the value.
     * @throws UnsupportedEncodingException is thrown if error decoding.
     */
    protected String shouldUrlDecodeHeader(NettyHttpConfiguration configuration, String headerName, Object value, String charset) throws UnsupportedEncodingException {
        // do not decode Content-Type
        if (Exchange.CONTENT_TYPE.equals(headerName)) {
            return value.toString();
        } else if (configuration.isUrlDecodeHeaders()) {
            return URLDecoder.decode(value.toString(), charset);
        } else {
            return value.toString();
        }
    }

    @Override
    public Message toCamelMessage(FullHttpResponse response, Exchange exchange, NettyHttpConfiguration configuration) throws Exception {
        LOG.trace("toCamelMessage: {}", response);

        NettyHttpMessage answer = new NettyHttpMessage(exchange.getContext(), null, response);
        answer.setExchange(exchange);
        if (configuration.isMapHeaders()) {
            populateCamelHeaders(response, answer.getHeaders(), exchange, configuration);
        }

        if (configuration.isDisableStreamCache() || configuration.isHttpProxy()) {
            // keep the body as is, and use type converters
            answer.setBody(response.content());
        } else {
            // stores as byte array as the netty ByteBuf will be freed when the producer is done, and then we can no longer access the message body
            response.retain();
            try {
                byte[] bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, response.content());
                answer.setBody(bytes);
            } finally {
                response.release();
            }
        }
        return answer;
    }

    @Override
    public void populateCamelHeaders(FullHttpResponse response, Map<String, Object> headers, Exchange exchange, NettyHttpConfiguration configuration) throws Exception {
        LOG.trace("populateCamelHeaders: {}", response);

        headers.put(Exchange.HTTP_RESPONSE_CODE, response.status().code());
        headers.put(Exchange.HTTP_RESPONSE_TEXT, response.status().reasonPhrase());

        for (String name : response.headers().names()) {
            // mapping the content-type
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }
            // add the headers one by one, and use the header filter strategy
            List<String> values = response.headers().getAll(name);
            Iterator<?> it = ObjectHelper.createIterator(values);
            while (it.hasNext()) {
                Object extracted = it.next();
                LOG.trace("HTTP-header: {}", extracted);
                if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                    NettyHttpHelper.appendHeader(headers, name, extracted);
                }
            }
        }
    }

    @Override
    public HttpResponse toNettyResponse(Message message, NettyHttpConfiguration configuration) throws Exception {
        LOG.trace("toNettyResponse: {}", message);

        if (message instanceof NettyHttpMessage) {
            final NettyHttpMessage nettyHttpMessage = (NettyHttpMessage) message;
            final FullHttpResponse response = nettyHttpMessage.getHttpResponse();

            if (response != null && nettyHttpMessage.getBody() == null) {
                return response.retain();
            }
        }

        // the message body may already be a Netty HTTP response
        if (message.getBody() instanceof HttpResponse) {
            return (HttpResponse) message.getBody();
        }

        Object body = message.getBody();
        Exception cause = message.getExchange().getException();
        // support bodies as native Netty
        ByteBuf buffer;
        // the response code is 200 for OK and 500 for failed
        boolean failed = message.getExchange().isFailed();
        int defaultCode = failed ? 500 : 200;

        int code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, defaultCode, int.class);

        LOG.trace("HTTP Status Code: {}", code);

        // if there was an exception then use that as body
        if (cause != null) {
            if (configuration.isTransferException()) {
                // we failed due an exception, and transfer it as java serialized object
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(cause);
                oos.flush();
                IOHelper.close(oos, bos);

                // the body should be the serialized java object of the exception
                body = NettyConverter.toByteBuffer(bos.toByteArray());
                // force content type to be serialized java object
                message.setHeader(Exchange.CONTENT_TYPE, NettyHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
            } else {
                // we failed due an exception so print it as plain text
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                cause.printStackTrace(pw);

                // the body should then be the stacktrace
                body = NettyConverter.toByteBuffer(sw.toString().getBytes());
                // force content type to be text/plain as that is what the stacktrace is
                message.setHeader(Exchange.CONTENT_TYPE, "text/plain");
            }

            // and mark the exception as failure handled, as we handled it by returning it as the response
            ExchangeHelper.setFailureHandled(message.getExchange());
        }

        HttpResponse response = null;

        if (response == null && body instanceof InputStream && configuration.isDisableStreamCache()) {
            response = new ChunkedHttpResponse((InputStream)body, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code)));
            response.headers().set(TRANSFER_ENCODING, CHUNKED);
        }

        if (response == null) {
            if (body instanceof ByteBuf) {
                buffer = (ByteBuf) body;
            } else {
                // try to convert to buffer first
                buffer = message.getBody(ByteBuf.class);
                if (buffer == null) {
                    // fallback to byte array as last resort
                    byte[] data = message.getBody(byte[].class);
                    if (data != null) {
                        buffer = NettyConverter.toByteBuffer(data);
                    } else {
                        // and if byte array fails then try String
                        String str;
                        if (body != null) {
                            str = message.getMandatoryBody(String.class);
                        } else {
                            str = "";
                        }
                        buffer = NettyConverter.toByteBuffer(str.getBytes());
                    }
                }
            }

            if (buffer != null) {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code), buffer);
                // We just need to reset the readerIndex this time
                if (buffer.readerIndex() == buffer.writerIndex()) {
                    buffer.setIndex(0, buffer.writerIndex());
                }
                // TODO How to enable the chunk transport
                int len = buffer.readableBytes();
                // set content-length
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH.toString(), len);
                LOG.trace("Content-Length: {}", len);
            } else {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code));
            }
        }

        TypeConverter tc = message.getExchange().getContext().getTypeConverter();

        // append headers
        // must use entrySet to ensure case of keys is preserved
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = ObjectHelper.createIterator(value, null, true);
            while (it.hasNext()) {
                String headerValue = tc.convertTo(String.class, it.next());
                if (headerValue != null && headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, message.getExchange())) {
                    LOG.trace("HTTP-Header: {}={}", key, headerValue);
                    response.headers().add(key, headerValue);
                }
            }
        }

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            response.headers().set(HttpHeaderNames.CONTENT_TYPE.toString(), contentType);
            LOG.trace("Content-Type: {}", contentType);
        }

        // configure connection to accordingly to keep alive configuration
        // favor using the header from the message
        String connection = message.getHeader(HttpHeaderNames.CONNECTION.toString(), String.class);
        // Read the connection header from the exchange property
        if (connection == null) {
            connection = message.getExchange().getProperty(HttpHeaderNames.CONNECTION.toString(), String.class);
        }
        if (connection == null) {
            // fallback and use the keep alive from the configuration
            if (configuration.isKeepAlive()) {
                connection = HttpHeaderValues.KEEP_ALIVE.toString();
            } else {
                connection = HttpHeaderValues.CLOSE.toString();
            }
        }
        response.headers().set(HttpHeaderNames.CONNECTION.toString(), connection);
        // Just make sure we close the channel when the connection value is close
        if (connection.equalsIgnoreCase(HttpHeaderValues.CLOSE.toString())) {
            message.setHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, true);
        }
        LOG.trace("Connection: {}", connection);

        return response;
    }

    @Override
    public HttpRequest toNettyRequest(Message message, String fullUri, NettyHttpConfiguration configuration) throws Exception {
        LOG.trace("toNettyRequest: {}", message);

        Object body = message.getBody();
        // the message body may already be a Netty HTTP response
        if (body instanceof HttpRequest) {
            return (HttpRequest) message.getBody();
        }

        String uriForRequest = fullUri;
        if (configuration.isUseRelativePath()) {
            final URI uri = new URI(uriForRequest);
            final String rawPath = uri.getRawPath();
            if (rawPath != null) {
                uriForRequest = rawPath;
            }
            final String rawQuery = uri.getRawQuery();
            if (rawQuery != null) {
                uriForRequest += "?" + rawQuery;
            }
        }

        final String headerProtocolVersion = message.getHeader(Exchange.HTTP_PROTOCOL_VERSION, String.class);
        final HttpVersion protocol;
        if (headerProtocolVersion == null) {
            protocol = HttpVersion.HTTP_1_1;
        } else {
            protocol = HttpVersion.valueOf(headerProtocolVersion);
        }

        final String headerMethod = message.getHeader(Exchange.HTTP_METHOD, String.class);

        final HttpMethod httpMethod;
        if (headerMethod == null) {
            httpMethod = HttpMethod.GET;
        } else {
            httpMethod = HttpMethod.valueOf(headerMethod);
        }

        final Exchange exchange = message.getExchange();
        final Object proxyRequest;
        if (exchange != null) {
            proxyRequest = exchange.getProperty(NettyHttpConstants.PROXY_REQUEST);
        } else {
            proxyRequest = null;
        }

        HttpRequest request = null;
        if (message instanceof NettyHttpMessage) {
            // if the request is already given we should set the values
            // from message headers and pass on the same request
            final FullHttpRequest givenRequest = ((NettyHttpMessage) message).getHttpRequest();
            // we need to make sure that the givenRequest is the original
            // request received by the proxy
            if (givenRequest != null && proxyRequest == givenRequest) {
                request = givenRequest
                        .setProtocolVersion(protocol)
                        .setMethod(httpMethod)
                        .setUri(uriForRequest);
            }
        }

        if (request == null && body instanceof InputStream && configuration.isDisableStreamCache()) {
            request = new ChunkedHttpRequest((InputStream)body, new DefaultHttpRequest(protocol, httpMethod, uriForRequest));
            request.headers().set(TRANSFER_ENCODING, CHUNKED);
        }

        if (request == null) {
            request = new DefaultFullHttpRequest(protocol, httpMethod, uriForRequest);

            if (body != null) {
                // support bodies as native Netty
                ByteBuf buffer;
                if (body instanceof ByteBuf) {
                    buffer = (ByteBuf) body;
                } else {
                    // try to convert to buffer first
                    buffer = message.getBody(ByteBuf.class);
                    if (buffer == null) {
                        // fallback to byte array as last resort
                        byte[] data = message.getMandatoryBody(byte[].class);

                        if (data.length > 0) {
                            buffer = NettyConverter.toByteBuffer(data);
                        }
                    }
                }


                if (buffer != null) {
                    if (buffer.readableBytes() > 0) {
                        request = ((DefaultFullHttpRequest)request).replace(buffer);
                        int len = buffer.readableBytes();
                        // set content-length
                        request.headers().set(HttpHeaderNames.CONTENT_LENGTH.toString(), len);
                        LOG.trace("Content-Length: {}", len);
                    } else {
                        buffer.release();
                    }
                }
            }

            // update HTTP method accordingly as we know if we have a body or not
            HttpMethod method = NettyHttpHelper.createMethod(message, body != null);
            request.setMethod(method);
        }


        TypeConverter tc = message.getExchange().getContext().getTypeConverter();

        // if we bridge endpoint then we need to skip matching headers with the HTTP_QUERY to avoid sending
        // duplicated headers to the receiver, so use this skipRequestHeaders as the list of headers to skip
        Map<String, Object> skipRequestHeaders = null;
        if (configuration.isBridgeEndpoint()) {
            String queryString = message.getHeader(Exchange.HTTP_QUERY, String.class);
            if (queryString != null) {
                skipRequestHeaders = URISupport.parseQuery(queryString, false, true);
            }
            // Need to remove the Host key as it should be not used
            message.getHeaders().remove("host");
        }

        // append headers
        // must use entrySet to ensure case of keys is preserved
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // we should not add headers for the parameters in the uri if we bridge the endpoint
            // as then we would duplicate headers on both the endpoint uri, and in HTTP headers as well
            if (skipRequestHeaders != null && skipRequestHeaders.containsKey(key)) {
                continue;
            }

            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = ObjectHelper.createIterator(value, null, true);
            while (it.hasNext()) {
                String headerValue = tc.convertTo(String.class, it.next());

                if (headerValue != null && headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, message.getExchange())) {
                    LOG.trace("HTTP-Header: {}={}", key, headerValue);
                    request.headers().add(key, headerValue);
                }
            }
        }

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            request.headers().set(HttpHeaderNames.CONTENT_TYPE.toString(), contentType);
            LOG.trace("Content-Type: {}", contentType);
        }

        // must include HOST header as required by HTTP 1.1
        // use URI as its faster than URL (no DNS lookup)
        URI u = new URI(fullUri);
        String hostHeader = u.getHost() + (u.getPort() == 80 ? "" : ":" + u.getPort());
        request.headers().set(HttpHeaderNames.HOST.toString(), hostHeader);
        LOG.trace("Host: {}", hostHeader);

        // configure connection to accordingly to keep alive configuration
        // favor using the header from the message
        String connection = message.getHeader(HttpHeaderNames.CONNECTION.toString(), String.class);
        if (connection == null) {
            // fallback and use the keep alive from the configuration
            if (configuration.isKeepAlive()) {
                connection = HttpHeaderValues.KEEP_ALIVE.toString();
            } else {
                connection = HttpHeaderValues.CLOSE.toString();
            }
        }
        request.headers().set(HttpHeaderNames.CONNECTION.toString(), connection);
        LOG.trace("Connection: {}", connection);

        return request;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }
}
