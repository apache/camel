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
package org.apache.camel.component.as2.api.util;

import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

public final class HttpMessageUtils {

    private HttpMessageUtils() {
    }

    public static String getHeaderValue(HttpMessage message, String headerName) {
        Header header = message.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
    }

    public static void setHeaderValue(HttpMessage message, String headerName, String headerValue) {
        Args.notNull(message, "message");
        Args.notNull(headerName, "headerName");
        if (headerValue == null) {
            message.removeHeaders(headerName);
        } else {
            message.setHeader(headerName, headerValue);
        }
    }

    public static <T> T getEntity(HttpMessage message, Class<T> type) {
        Args.notNull(message, "message");
        Args.notNull(type, "type");
        if (message instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest)message).getEntity();
            if (entity != null && type.isInstance(entity)) {
                return type.cast(entity);
            }
        } else if (message instanceof HttpResponse) {
            HttpEntity entity = ((HttpResponse)message).getEntity();
            if (entity != null && type.isInstance(entity)) {
                type.cast(entity);
            }
        }
        return null;
    }

    public static String parseBodyPartContent(SessionInputBuffer inBuffer, String boundary) throws HttpException {
        try {
            CharArrayBuffer bodyPartContentBuffer = new CharArrayBuffer(1024);
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            boolean foundMultipartEndBoundary = false;
            while (inBuffer.readLine(lineBuffer) != -1) {
                if (EntityParser.isBoundaryDelimiter(lineBuffer, null, boundary)) {
                    foundMultipartEndBoundary = true;
                    // Remove previous line ending: this is associated with
                    // boundary
                    bodyPartContentBuffer.setLength(bodyPartContentBuffer.length() - 2);
                    lineBuffer.clear();
                    break;
                }
                lineBuffer.append("\r\n"); // add line delimiter
                bodyPartContentBuffer.append(lineBuffer);
                lineBuffer.clear();
            }
            if (!foundMultipartEndBoundary) {
                throw new HttpException("Failed to find end boundary delimiter for body part");
            }

            return bodyPartContentBuffer.toString();
        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse body part content", e);
        }
    }

    public static String getParameterValue(HttpMessage message, String headerName, String parameterName) {
        Args.notNull(message, "message");
        Args.notNull(headerName, "headerName");
        Args.notNull(parameterName, "parameterName");
        Header header = message.getFirstHeader(headerName);
        if (header == null) {
            return null;
        }
        for (HeaderElement headerElement : header.getElements()) {
            for (NameValuePair nameValuePair : headerElement.getParameters()) {
                if (nameValuePair.getName().equalsIgnoreCase(parameterName)) {
                    return nameValuePair.getValue();
                }
            }
        }
        return null;
    }

}
