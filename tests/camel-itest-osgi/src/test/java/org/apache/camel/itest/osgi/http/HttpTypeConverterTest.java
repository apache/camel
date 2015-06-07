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
package org.apache.camel.itest.osgi.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class HttpTypeConverterTest extends OSGiIntegrationTestSupport {

    static HttpServletResponse servletResponse = new HttpServletResponse() {
        public void addCookie(Cookie cookie) {
        }

        public void addDateHeader(String name, long date) {
        }

        public void addHeader(String name, String value) {
        }

        public void addIntHeader(String name, int value) {
        }

        public boolean containsHeader(String name) {
            return false;
        }

        public String encodeRedirectURL(String url) {
            return null;
        }

        public String encodeRedirectUrl(String url) {
            return null;
        }

        public String encodeURL(String url) {
            return null;
        }

        public String encodeUrl(String url) {
            return null;
        }

        public void sendError(int sc) throws IOException {
        }

        public void sendError(int sc, String msg) throws IOException {
        }

        public void sendRedirect(String location) throws IOException {
        }

        public void setDateHeader(String name, long date) {
        }

        public void setHeader(String name, String value) {
        }

        public void setIntHeader(String name, int value) {
        }

        public void setStatus(int sc) {
        }

        public void setStatus(int sc, String sm) {
        }

        public void flushBuffer() throws IOException {
        }

        public int getBufferSize() {
            return 1024;
        }

        public String getCharacterEncoding() {
            return null;
        }

        public String getContentType() {
            return null;
        }

        public String getHeader(String s) {
            return null;
        }

        public Collection<String> getHeaderNames() {
            return null;
        }

        public Collection<String> getHeaders(String s) {
            return null;
        }

        public int getStatus() {
            return 0;
        }

        public Locale getLocale() {
            return null;
        }

        public ServletOutputStream getOutputStream() throws IOException {
            return null;
        }

        public PrintWriter getWriter() throws IOException {
            return null;
        }

        public boolean isCommitted() {
            return true;
        }

        public void reset() {
        }

        public void resetBuffer() {
        }

        public void setBufferSize(int size) {
        }

        public void setCharacterEncoding(String charset) {
        }

        public void setContentLength(int len) {
        }

        public void setContentType(String type) {
        }

        public void setLocale(Locale loc) {
        }

    };
    
    public boolean isUseRouteBuilder() {
        return false;
    }
    
    @Test
    public void testHttpConverter() throws Exception {
        Message message = new DefaultMessage();
        message.setHeader(Exchange.HTTP_SERVLET_RESPONSE, servletResponse);
        HttpServletResponse result = context.getTypeConverter().convertTo(HttpServletResponse.class, message);
        Assert.assertNotNull("The http converter doesn't work", result);
        
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-http"));
        
        return options;
    }
   

}
