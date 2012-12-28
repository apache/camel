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
package org.apache.camel.component.http4.helper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Special adapter when {@link org.apache.camel.component.http4.HttpServletUrlRewrite} is in use,
 * and the route started from came-jetty/camel-serlvet.
 * <p/>
 * This adapter ensures that we can control the context-path returned from the
 * {@link javax.servlet.http.HttpServletRequest#getContextPath()} method.
 * This allows us to ensure the context-path is based on the endpoint path, as the
 * camel-jetty/camel-servlet server implementation uses the root ("/") context-path
 * for all the servlets/endpoints.
 */
public final class UrlRewriteHttpServletRequestAdapter implements HttpServletRequest {

    private final HttpServletRequest delegate;
    private final String contextPath;

    /**
     * Creates this adapter
     * @param delegate    the real http servlet request to delegate.
     * @param contextPath use to override and return this context-path
     */
    public UrlRewriteHttpServletRequestAdapter(HttpServletRequest delegate, String contextPath) {
        this.delegate = delegate;
        this.contextPath = contextPath;
    }

    public String getAuthType() {
        return delegate.getAuthType();
    }

    public Enumeration getHeaderNames() {
        return delegate.getHeaderNames();
    }

    public String getPathInfo() {
        return delegate.getPathInfo();
    }

    public Object getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    public Enumeration getParameterNames() {
        return delegate.getParameterNames();
    }

    public String getProtocol() {
        return delegate.getProtocol();
    }

    public String getHeader(String name) {
        return delegate.getHeader(name);
    }

    public String getQueryString() {
        return delegate.getQueryString();
    }

    public Enumeration getHeaders(String name) {
        return delegate.getHeaders(name);
    }

    public String getRemoteUser() {
        return delegate.getRemoteUser();
    }

    public int getRemotePort() {
        return delegate.getRemotePort();
    }

    public void removeAttribute(String name) {
        delegate.removeAttribute(name);
    }

    public long getDateHeader(String name) {
        return delegate.getDateHeader(name);
    }

    public HttpSession getSession() {
        return delegate.getSession();
    }

    public boolean isSecure() {
        return delegate.isSecure();
    }

    public int getContentLength() {
        return delegate.getContentLength();
    }

    public BufferedReader getReader() throws IOException {
        return delegate.getReader();
    }

    public Locale getLocale() {
        return delegate.getLocale();
    }

    public Map getParameterMap() {
        return delegate.getParameterMap();
    }

    public Enumeration getLocales() {
        return delegate.getLocales();
    }

    public HttpSession getSession(boolean create) {
        return delegate.getSession(create);
    }

    public int getLocalPort() {
        return delegate.getLocalPort();
    }

    public String getServerName() {
        return delegate.getServerName();
    }

    public void setAttribute(String name, Object o) {
        delegate.setAttribute(name, o);
    }

    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        delegate.setCharacterEncoding(env);
    }

    public Enumeration getAttributeNames() {
        return delegate.getAttributeNames();
    }

    public String getRequestedSessionId() {
        return delegate.getRequestedSessionId();
    }

    public String getRemoteHost() {
        return delegate.getRemoteHost();
    }

    public boolean isRequestedSessionIdValid() {
        return delegate.isRequestedSessionIdValid();
    }

    public String getPathTranslated() {
        return delegate.getPathTranslated();
    }

    public String getMethod() {
        return delegate.getMethod();
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        return delegate.getRequestDispatcher(path);
    }

    public String getScheme() {
        return delegate.getScheme();
    }

    public String getCharacterEncoding() {
        return delegate.getCharacterEncoding();
    }

    public StringBuffer getRequestURL() {
        return delegate.getRequestURL();
    }

    public int getServerPort() {
        return delegate.getServerPort();
    }

    public boolean isRequestedSessionIdFromCookie() {
        return delegate.isRequestedSessionIdFromCookie();
    }

    public String getLocalAddr() {
        return delegate.getLocalAddr();
    }

    public boolean isRequestedSessionIdFromUrl() {
        return delegate.isRequestedSessionIdFromUrl();
    }

    public Cookie[] getCookies() {
        return delegate.getCookies();
    }

    public String getRemoteAddr() {
        return delegate.getRemoteAddr();
    }

    public Principal getUserPrincipal() {
        return delegate.getUserPrincipal();
    }

    public String[] getParameterValues(String name) {
        return delegate.getParameterValues(name);
    }

    public String getContentType() {
        return delegate.getContentType();
    }

    public String getParameter(String name) {
        return delegate.getParameter(name);
    }

    public String getLocalName() {
        return delegate.getLocalName();
    }

    public String getContextPath() {
        return contextPath != null ? contextPath : delegate.getContextPath();
    }

    public String getRealPath(String path) {
        return delegate.getRealPath(path);
    }

    public int getIntHeader(String name) {
        return delegate.getIntHeader(name);
    }

    public boolean isUserInRole(String role) {
        return delegate.isUserInRole(role);
    }

    public boolean isRequestedSessionIdFromURL() {
        return delegate.isRequestedSessionIdFromURL();
    }

    public ServletInputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    public String getRequestURI() {
        return delegate.getRequestURI();
    }

    public String getServletPath() {
        return delegate.getServletPath();
    }
}
