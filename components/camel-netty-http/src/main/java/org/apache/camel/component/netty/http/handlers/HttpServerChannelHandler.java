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

import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.util.Locale;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
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
import org.apache.camel.component.netty.http.NettyHttpSecurityConfiguration;
import org.apache.camel.component.netty.http.SecurityAuthenticator;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
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
        if (msg instanceof HttpRequest) {
            request = (HttpRequest) msg;
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
        NettyHttpSecurityConfiguration security = consumer.getEndpoint().getSecurityConfiguration();
        if (security != null && security.isAuthenticate() && "Basic".equalsIgnoreCase(security.getConstraint())) {
            String url = request.uri();

            // drop parameters from url
            if (url.contains("?")) {
                url = StringHelper.before(url, "?");
            }

            // we need the relative path without the hostname and port
            URI uri = new URI(request.uri());
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
                }
            }
        }

        // let Camel process this message
        super.channelRead0(ctx, msg);
    }

    private String extractTarget(URI uri) {
        String target = uri.getPath();

        // strip the starting endpoint path so the target is relative to the endpoint uri
        String path = consumer.getConfiguration().getPath();
        if (path != null && target.startsWith(path)) {
            // need to match by lower case as we want to ignore case on context-path
            path = path.toLowerCase(Locale.US);
            String match = target.toLowerCase(Locale.US);
            if (match.startsWith(path)) {
                target = target.substring(path.length());
            }
        }
        return target;
    }

    protected boolean matchesRoles(String roles, String userRoles) {
        // matches if no role restrictions or any role is accepted
        if (roles.equals("*")) {
            return true;
        }

        // see if any of the user roles is contained in the roles list
        for (String userRole : ObjectHelper.createIterable(userRoles)) {
            if (roles.contains(userRole)) {
                return true;
            }
        }

        return false;
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
                    String decoded = StringHelper.after(auth, " ");
                    // the decoded part is base64 encoded, so we need to decode that
                    ByteBuf buf = NettyConverter.toByteBuffer(decoded.getBytes());
                    ByteBuf out = Base64.decode(buf);
                    try {
                        String userAndPw = out.toString(Charset.defaultCharset());
                        String username = StringHelper.before(userAndPw, ":");
                        String password = StringHelper.after(userAndPw, ":");
                        HttpPrincipal principal = new HttpPrincipal(username, password);
                        LOG.debug("Extracted Basic Auth principal from HTTP header: {}", principal);
                        return principal;
                    } finally {
                        buf.release();
                        out.release();
                    }
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
        if (message instanceof HttpRequest) {
            request = (HttpRequest) message;
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
        return consumer.getEndpoint().getNettyHttpBinding().toNettyResponse(exchange.getMessage(), consumer.getConfiguration());
    }

    @Override
    protected Exchange createExchange(ChannelHandlerContext ctx, Object message) throws Exception {
        Exchange exchange = this.consumer.createExchange(false);

        // create a new IN message as we cannot reuse with netty
        Message in;
        if (message instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) message;
            in = consumer.getEndpoint().getNettyHttpBinding().toCamelMessage(request, exchange, consumer.getConfiguration());
        } else {
            InboundStreamHttpRequest request = (InboundStreamHttpRequest) message;
            in = consumer.getEndpoint().getNettyHttpBinding().toCamelMessage(request, exchange, consumer.getConfiguration());
        }
        exchange.setIn(in);

        // setup the common message headers
        consumer.getEndpoint().updateMessageHeader(in, ctx);

        // honor the character encoding
        String contentType = in.getHeader(NettyHttpConstants.CONTENT_TYPE, String.class);
        String charset = org.apache.camel.support.http.HttpUtil.getCharsetFromContentType(contentType);
        if (charset != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, charset);
            in.setHeader(NettyHttpConstants.HTTP_CHARACTER_ENCODING, charset);
        }

        return exchange;
    }
}
