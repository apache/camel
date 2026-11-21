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
package org.apache.camel.component.netty.http.handlers;

import java.io.InputStream;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.component.netty.NettyConverter;
import org.apache.camel.component.netty.NettyHelper;
import org.apache.camel.component.netty.handlers.ServerChannelHandler;
import org.apache.camel.component.netty.http.HttpPrincipal;
import org.apache.camel.component.netty.http.InboundStreamHttpRequest;
import org.apache.camel.component.netty.http.NettyHttpConfiguration;
import org.apache.camel.component.netty.http.NettyHttpConstants;
import org.apache.camel.component.netty.http.NettyHttpConsumer;
import org.apache.camel.component.netty.http.NettyHttpHelper;
import org.apache.camel.component.netty.http.NettyHttpSecurityConfiguration;
import org.apache.camel.component.netty.http.OutboundStreamHttpResponse;
import org.apache.camel.component.netty.http.SecurityAuthenticator;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.http.base.OAuthHttpSecuritySupport.Validation;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.TlsPeerCertificateSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Netty HTTP {@link ServerChannelHandler} that handles the incoming HTTP requests and routes the received message in
 * Camel.
 */
public class HttpServerChannelHandler extends ServerChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerChannelHandler.class);

    // internal exchange property: the reply that is streamed by the outbound streamer, which reads it after the exchange
    // is done
    private static final String REPLY_STREAM = "CamelNettyHttpReplyStream";

    // whether the basic auth credential of the request being read has been checked by Camel, so that beforeProcess
    // (called by channelRead0 of the super class on the same thread) removes the Authorization header from the message
    private static final AttributeKey<Boolean> BASIC_AUTH_CHECKED
            = AttributeKey.valueOf(HttpServerChannelHandler.class.getName() + ".basicAuthChecked");
    private final NettyHttpConsumer consumer;

    public HttpServerChannelHandler(NettyHttpConsumer consumer) {
        super(consumer);
        this.consumer = consumer;
    }

    public NettyHttpConsumer getConsumer() {
        return consumer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        HttpRequest request;
        if (msg instanceof HttpRequest httpReq) {
            request = httpReq;
        } else {
            request = ((InboundStreamHttpRequest) msg).getHttpRequest();
        }

        LOG.debug("Message received: {}", request);

        DecoderResult decoderResult = request.decoderResult();
        if (decoderResult != null && decoderResult.cause() != null) {
            if (getConsumer().getConfiguration().isLogWarnOnBadRequest()) {
                LOG.warn("Netty request decoder failure due: {} returning HTTP Status 400 to client",
                        decoderResult.cause().getMessage());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Netty request decoder failure (stacktrace)", decoderResult.cause());
                }
            }
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, BAD_REQUEST);
            response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
            response.headers().set(Exchange.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response);
            ctx.channel().close();
            return;
        }

        if (consumer.isSuspended()) {
            // are we suspended?
            LOG.debug("Consumer suspended, cannot service request {}", request);
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, SERVICE_UNAVAILABLE);
            response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
            response.headers().set(Exchange.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response);
            ctx.channel().close();
            return;
        }

        if (consumer.getEndpoint().getHttpMethodRestrict() != null
                && !consumer.getEndpoint().getHttpMethodRestrict().contains(request.method().name())) {
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED);
            response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
            response.headers().set(Exchange.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response);
            ctx.channel().close();
            return;
        }
        if ("TRACE".equals(request.method().name()) && !consumer.getEndpoint().isTraceEnabled()) {
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED);
            response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
            response.headers().set(Exchange.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response);
            ctx.channel().close();
            return;
        }
        // must include HOST header as required by HTTP 1.1
        if (!request.headers().contains(HttpHeaderNames.HOST.toString())) {
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, BAD_REQUEST);
            //response.setChunked(false);
            response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
            response.headers().set(Exchange.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response);
            ctx.channel().close();
            return;
        }

        // is basic auth configured
        boolean basicAuthChecked = false;
        NettyHttpSecurityConfiguration security = consumer.getEndpoint().getSecurityConfiguration();
        if (security != null && security.isAuthenticate() && "Basic".equalsIgnoreCase(security.getConstraint())) {
            String url = request.uri();

            // drop parameters from url
            if (url.contains("?")) {
                url = StringHelper.before(url, "?");
            }

            // we need the relative path without the hostname and port
            URI uri = new URI(request.uri());

            // a request path with . or .. segments is ambiguous: the security constraint, the consumer dispatch
            // and the route may each interpret it differently, so a path such as /foo/../admin/x (or its percent-encoded
            // /foo/%2e%2e/admin/x form) could be matched against the constraint inclusions/exclusions in a different
            // form than the one the route handles. Reject it up front when a security constraint is configured; a
            // well-behaved HTTP client normalizes dot-segments before sending (CAMEL-25705). The same applies to a path
            // with an empty segment, a ; path parameter or a \ (also in their percent-encoded forms), which a backend
            // the route forwards to may collapse, strip or treat as a / respectively. A request target that starts with
            // // is parsed as a host followed by a path, so its first segment never reaches uri.getPath(); check the
            // request target for that empty segment.
            if (security.getSecurityConstraint() != null
                    && (request.uri().startsWith("//") || isAmbiguousPath(uri.getPath()))) {
                LOG.debug("Rejecting request with an ambiguous path for resource: {}", url);
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, BAD_REQUEST);
                response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
                response.headers().set(Exchange.CONTENT_LENGTH, 0);
                ctx.writeAndFlush(response);
                ctx.channel().close();
                return;
            }

            final String target = extractTarget(uri);

            // is it a restricted resource?
            String roles;
            if (security.getSecurityConstraint() != null) {
                // if restricted returns null, then the resource is not restricted and we should not authenticate the user
                roles = security.getSecurityConstraint().restricted(target);
            } else {
                // assume any roles is valid if no security constraint has been configured
                roles = "*";
            }
            if (roles != null) {
                // basic auth subject
                HttpPrincipal principal = extractBasicAuthSubject(request);

                // authenticate principal and check if the user is in role
                Subject subject = null;
                boolean inRole = true;
                if (principal != null) {
                    subject = authenticate(security.getSecurityAuthenticator(), security.getLoginDeniedLoggingLevel(),
                            principal);
                    if (subject != null) {
                        String userRoles = security.getSecurityAuthenticator().getUserRoles(subject);
                        inRole = matchesRoles(roles, userRoles);
                    }
                }

                if (principal == null || subject == null || !inRole) {
                    if (principal == null) {
                        LOG.debug("Http Basic Auth required for resource: {}", url);
                    } else if (subject == null) {
                        LOG.debug("Http Basic Auth not authorized for username: {}", principal.getUsername());
                    } else {
                        LOG.debug("Http Basic Auth not in role for username: {}", principal.getUsername());
                    }
                    // restricted resource, so send back 401 to require valid username/password
                    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, UNAUTHORIZED);
                    response.headers().set("WWW-Authenticate", "Basic realm=\"" + security.getRealm() + "\"");
                    response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
                    response.headers().set(Exchange.CONTENT_LENGTH, 0);
                    ctx.writeAndFlush(response);
                    // close the channel
                    ctx.channel().close();
                    return;
                } else {
                    LOG.debug("Http Basic Auth authorized for username: {}", principal.getUsername());
                    basicAuthChecked = true;
                }
            }
        }

        // let Camel process this message
        ctx.channel().attr(BASIC_AUTH_CHECKED).set(basicAuthChecked);
        super.channelRead0(ctx, msg);
    }

    private String extractTarget(URI uri) {
        String target = uri.getPath();

        // strip the starting endpoint path so the target is relative to the endpoint uri.
        // the comparison must ignore case on the context-path, the same way consumer dispatch does
        // (RestConsumerContextPathMatcher), so the security constraint is evaluated against the same
        // target the request is actually routed to
        String path = consumer.getConfiguration().getPath();
        if (path != null && target.toLowerCase(Locale.US).startsWith(path.toLowerCase(Locale.US))) {
            target = target.substring(path.length());
        }
        return target;
    }

    /**
     * Whether the (decoded) path may be interpreted differently by the security constraint, the consumer dispatch, the
     * route or a backend the route forwards to: it contains a {@code .} or {@code ..} segment, an empty segment
     * ({@code //}), a {@code ;} path parameter or a {@code \}. Such a path is rejected up front when a security
     * constraint is configured (see {@link #channelRead0}).
     */
    static boolean isAmbiguousPath(String path) {
        if (path == null) {
            return false;
        }
        return path.indexOf(';') != -1 || path.indexOf('\\') != -1 || path.contains("//") || containsDotSegments(path);
    }

    /**
     * Whether the path contains a {@code .} or {@code ..} segment that path normalization would resolve. Such a path
     * may be interpreted differently by the security constraint, the consumer dispatch and the route, so it is rejected
     * up front when a security constraint is configured (see {@link #channelRead0}).
     */
    private static boolean containsDotSegments(String path) {
        return path != null && !path.equals(removeDotSegments(path));
    }

    /**
     * Removes {@code .} and {@code ..} segments from the given path, as specified by the {@code remove_dot_segments}
     * algorithm in RFC 3986 section 5.2.4. Used to detect whether a request path carries dot-segments. Excess
     * {@code ..} segments cannot climb above the root, so the returned path never contains a {@code ..} segment.
     */
    static String removeDotSegments(String path) {
        // fast path: nothing to resolve unless there is a dot segment to begin with
        if (path == null || path.isEmpty() || path.indexOf('.') == -1) {
            return path;
        }

        String in = path;
        StringBuilder out = new StringBuilder(path.length());
        while (!in.isEmpty()) {
            if (in.startsWith("../")) {
                in = in.substring(3);
            } else if (in.startsWith("./")) {
                in = in.substring(2);
            } else if (in.startsWith("/./")) {
                in = in.substring(2);
            } else if (in.equals("/.")) {
                in = "/";
            } else if (in.startsWith("/../")) {
                in = in.substring(3);
                removeLastSegment(out);
            } else if (in.equals("/..")) {
                in = "/";
                removeLastSegment(out);
            } else if (in.equals(".") || in.equals("..")) {
                in = "";
            } else {
                // move the first path segment (including any leading slash, up to but not including the
                // next slash) from the input to the output
                int start = in.charAt(0) == '/' ? 1 : 0;
                int next = in.indexOf('/', start);
                if (next < 0) {
                    out.append(in);
                    in = "";
                } else {
                    out.append(in, 0, next);
                    in = in.substring(next);
                }
            }
        }
        return out.toString();
    }

    private static void removeLastSegment(StringBuilder out) {
        int lastSlash = out.lastIndexOf("/");
        if (lastSlash >= 0) {
            out.setLength(lastSlash);
        } else {
            out.setLength(0);
        }
    }

    protected boolean matchesRoles(String roles, String userRoles) {
        // matches if no role restrictions or any role is accepted
        if (roles.equals("*")) {
            return true;
        }

        // the user must have one of the roles, compared by the exact role name
        Set<String> names = roleNames(roles);
        for (String userRole : ObjectHelper.createIterable(userRoles)) {
            if (names.contains(userRole.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * The role names in the comma separated list of roles, trimmed and without blank entries.
     */
    private static Set<String> roleNames(String roles) {
        Set<String> names = new HashSet<>();
        for (String role : ObjectHelper.createIterable(roles)) {
            String name = role.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Extracts the username and password details from the HTTP basic header Authorization.
     * <p/>
     * This requires that the <tt>Authorization</tt> HTTP header is provided, and its using Basic. Currently Digest is
     * <b>not</b> supported.
     *
     * @return {@link HttpPrincipal} with username and password details, or <tt>null</tt> if not possible to extract
     */
    protected static HttpPrincipal extractBasicAuthSubject(HttpRequest request) {
        String auth = request.headers().get("Authorization");
        if (auth != null) {
            String constraint = StringHelper.before(auth, " ");
            if (constraint != null) {
                if ("Basic".equalsIgnoreCase(constraint.trim())) {
                    String encoded = StringHelper.after(auth, " ");
                    byte[] credentials;
                    try {
                        // the value is base64 encoded, so we need to decode that
                        credentials = Base64.getDecoder().decode(encoded.trim());
                    } catch (IllegalArgumentException e) {
                        // a client sent something that is not base64 at all: no principal, so the request is
                        // answered with 401 rather than an error
                        LOG.debug("Cannot decode the Basic Auth header, it is not valid base64");
                        return null;
                    }
                    // RFC 7617: the credentials are encoded with UTF-8, so the platform default charset must not
                    // decide how a non-ASCII user name or password is read back
                    String userAndPw = new String(credentials, StandardCharsets.UTF_8);
                    String username = StringHelper.before(userAndPw, ":");
                    String password = StringHelper.after(userAndPw, ":");
                    HttpPrincipal principal = new HttpPrincipal(username, password);
                    LOG.debug("Extracted Basic Auth principal from HTTP header: {}", principal);
                    return principal;
                }
            }
        }
        return null;
    }

    /**
     * Authenticates the http basic auth subject.
     *
     * @param  authenticator the authenticator
     * @param  principal     the principal
     * @return               <tt>true</tt> if username and password is valid, <tt>false</tt> if not
     */
    protected Subject authenticate(
            SecurityAuthenticator authenticator, LoggingLevel deniedLoggingLevel, HttpPrincipal principal) {
        try {
            return authenticator.login(principal);
        } catch (LoginException e) {
            CamelLogger logger = new CamelLogger(LOG, deniedLoggingLevel);
            logger.log("Cannot login " + principal.getName() + " due " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    protected void beforeProcess(Exchange exchange, final ChannelHandlerContext ctx, final Object message) {
        final NettyHttpConfiguration configuration = consumer.getConfiguration();

        if (configuration.isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            exchange.setProperty(Exchange.SKIP_WWW_FORM_URLENCODED, Boolean.TRUE);
        }

        HttpRequest request;
        if (message instanceof HttpRequest httpReq) {
            request = httpReq;
        } else {
            request = ((InboundStreamHttpRequest) message).getHttpRequest();
        }
        // setup the connection property in case of the message header is removed
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (!keepAlive) {
            // Just make sure we close the connection this time.
            exchange.setProperty(NettyHttpConstants.CONNECTION, HttpHeaderValues.CLOSE.toString());
        }

        final Message in = exchange.getIn();
        if (Boolean.TRUE.equals(ctx.channel().attr(BASIC_AUTH_CHECKED).getAndSet(null))) {
            // the credential has been checked by Camel, so it is not passed on to the route
            OAuthHttpSecuritySupport.removeAuthorizationHeader(in);
        }
        OAuthHttpSecuritySupport security = consumer.getEndpoint().getOauthHttpSecurity();
        if (security != null) {
            Validation validation = security.validate(
                    exchange.getContext(), request.headers().getAll(HttpHeaderNames.AUTHORIZATION));
            request.headers().remove(HttpHeaderNames.AUTHORIZATION);
            OAuthHttpSecuritySupport.removeAuthorizationHeader(in);
            if (validation.isAuthenticated()) {
                OAuthHttpSecuritySupport.applyAuthenticatedResult(exchange, validation.getValidationResult());
            } else {
                OAuthHttpSecuritySupport.reject(exchange, validation);
                return;
            }
        }

        if (configuration.isHttpProxy()) {
            in.removeHeader("Proxy-Connection");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // only close if we are still allowed to run
        if (consumer.isRunAllowed()) {
            if (cause instanceof ClosedChannelException) {
                LOG.debug("Channel already closed. Ignoring this exception.");
            } else {
                LOG.debug("Closing channel as an exception was thrown from Netty", cause);
                // close channel in case an exception was thrown
                NettyHelper.close(ctx.channel());
            }
        }
    }

    @Override
    protected Object getResponseBody(Exchange exchange) throws Exception {
        try {
            HttpResponse response = consumer.getEndpoint().getNettyHttpBinding().toNettyResponse(exchange.getMessage(),
                    consumer.getConfiguration());
            if (response instanceof OutboundStreamHttpResponse reply) {
                // the outbound streamer writes the body of the reply after the exchange is done, and may read the body
                // stream of the request (sent back, as it is or in another stream), which is then closed with the reply
                StreamedReply streamedReply = new StreamedReply(reply);
                exchange.setProperty(REPLY_STREAM, streamedReply);
                return streamedReply;
            }
            return response;
        } catch (Exception e) {
            // no response can be built from the message, so answer with a 500, otherwise the client gets no response
            // and waits for one forever (if not timing out)
            consumer.getExceptionHandler().handleException(
                    "Cannot build the HTTP response, returning HTTP Status 500 to client", exchange, e);
            return createFailureResponse(exchange, e);
        }
    }

    /**
     * The HTTP Status 500 response to send when no response can be built from the message. As for a failed exchange,
     * the body is empty when muteException is enabled, otherwise it is the stacktrace of the exception. The channel is
     * closed once the response is written, or fails to be written.
     */
    private HttpResponse createFailureResponse(Exchange exchange, Exception cause) {
        String text = consumer.getConfiguration().isMuteException() ? "" : ExceptionHelper.stackTraceToString(cause);
        ByteBuf buffer = NettyConverter.toByteBuffer(text.getBytes(StandardCharsets.UTF_8));
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR, buffer);
        response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(Exchange.CONTENT_LENGTH, buffer.readableBytes());
        response.headers().set(NettyHttpConstants.CONNECTION, HttpHeaderValues.CLOSE.toString());
        exchange.getMessage().setHeader(NettyHttpConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, true);
        return response;
    }

    static void populateTlsPeer(Exchange exchange, ChannelHandlerContext context) {
        var ssl = context.pipeline().get(SslHandler.class);
        TlsPeerCertificateSupport.populateFromSession(exchange, ssl != null ? ssl.engine().getSession() : null);
    }

    @Override
    protected Exchange createExchange(ChannelHandlerContext ctx, Object message) throws Exception {
        Exchange exchange = this.consumer.createExchange(false);
        populateTlsPeer(exchange, ctx);

        // create a new IN message as we cannot reuse with netty
        Message in;
        if (message instanceof FullHttpRequest request) {
            in = consumer.getEndpoint().getNettyHttpBinding().toCamelMessage(request, exchange, consumer.getConfiguration());
        } else {
            InboundStreamHttpRequest request = (InboundStreamHttpRequest) message;
            in = consumer.getEndpoint().getNettyHttpBinding().toCamelMessage(request, exchange, consumer.getConfiguration());
            // close the body stream when the exchange is done, so the part of the body that the route did not read is
            // discarded, and the connection is read again
            InputStream body = request.getInputStream();
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    // unless the reply is streamed: the outbound streamer writes it after the exchange is done, and it may
                    // read the body, so the body stream is closed with the reply
                    StreamedReply reply = exchange.getProperty(REPLY_STREAM, StreamedReply.class);
                    if (reply != null) {
                        reply.closeWith(body);
                    } else {
                        IOHelper.close(body);
                    }
                }
            });
        }
        exchange.setIn(in);

        // setup the common message headers
        consumer.getEndpoint().updateMessageHeader(in, ctx);

        // honor the character encoding
        String contentType = in.getHeader(NettyHttpConstants.CONTENT_TYPE, String.class);
        String charset = NettyHttpHelper.getCharsetFromContentType(contentType);
        if (charset != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, charset);
            in.setHeader(NettyHttpConstants.HTTP_CHARACTER_ENCODING, charset);
        }

        return exchange;
    }

    /**
     * A streamed reply that closes the body stream of the request with it. The outbound streamer closes the reply once
     * it is written, when the write fails, or when the connection is closed. Everything else is the reply as built by
     * the binding.
     */
    private static final class StreamedReply extends OutboundStreamHttpResponse {
        private final OutboundStreamHttpResponse reply;
        private boolean closed;
        private InputStream requestStream;

        StreamedReply(OutboundStreamHttpResponse reply) {
            super(InputStream.nullInputStream(), reply);
            this.reply = reply;
        }

        /**
         * Closes the body stream of the request when the reply is closed, or now if the reply is closed already.
         */
        void closeWith(InputStream stream) {
            synchronized (this) {
                if (!closed) {
                    requestStream = stream;
                    return;
                }
            }
            IOHelper.close(stream);
        }

        @Override
        public HttpVersion protocolVersion() {
            return reply.protocolVersion();
        }

        @Override
        public HttpResponseStatus status() {
            return reply.status();
        }

        @Override
        public HttpHeaders headers() {
            return reply.headers();
        }

        @Override
        public DefaultHttpResponse getResponse() {
            return reply.getResponse();
        }

        @Override
        public boolean isEndOfInput() throws Exception {
            return reply.isEndOfInput();
        }

        @Override
        public void close() throws Exception {
            InputStream stream;
            synchronized (this) {
                closed = true;
                stream = requestStream;
                requestStream = null;
            }
            try {
                reply.close();
            } finally {
                IOHelper.close(stream);
            }
        }

        @Override
        @Deprecated
        public HttpContent readChunk(ChannelHandlerContext ctx) throws Exception {
            return reply.readChunk(ctx);
        }

        @Override
        public HttpContent readChunk(ByteBufAllocator allocator) throws Exception {
            return reply.readChunk(allocator);
        }

        @Override
        public long length() {
            return reply.length();
        }

        @Override
        public long progress() {
            return reply.progress();
        }
    }
}
