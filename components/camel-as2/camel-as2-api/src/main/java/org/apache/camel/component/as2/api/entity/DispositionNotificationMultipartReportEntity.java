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
package org.apache.camel.component.as2.api.entity;

import java.io.StringWriter;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DispositionNotificationMultipartReportEntity extends MultipartReportEntity {
    
    private VelocityEngine velocityEngine;

    protected DispositionNotificationMultipartReportEntity(String boundary, boolean isMainBody) {
        this.boundary = boundary;
        this.isMainBody = isMainBody;
        removeHeaders(AS2Header.CONTENT_TYPE);
        setContentType(getContentTypeValue(boundary));
    }

    public DispositionNotificationMultipartReportEntity(HttpEntityEnclosingRequest request,
                                                        HttpResponse response,
                                                        DispositionMode dispositionMode,
                                                        AS2DispositionType dispositionType,
                                                        AS2DispositionModifier dispositionModifier,
                                                        String[] failureFields,
                                                        String[] errorFields,
                                                        String[] warningFields,
                                                        Map<String, String> extensionFields,
                                                        String charset,
                                                        String boundary,
                                                        boolean isMainBody,
                                                        PrivateKey decryptingPrivateKey)
            throws HttpException {
        super(charset, isMainBody, boundary);
        removeHeaders(AS2Header.CONTENT_TYPE);
        setContentType(getContentTypeValue(boundary));

        addPart(buildPlainTextReport(request, response, dispositionMode, dispositionType, dispositionModifier,
                failureFields, errorFields, warningFields, extensionFields, charset));
        addPart(new AS2MessageDispositionNotificationEntity(request, response, dispositionMode, dispositionType,
                dispositionModifier, failureFields, errorFields, warningFields, extensionFields, charset, false, decryptingPrivateKey));
    }

    public String getMainMessageContentType() {
        return AS2MimeType.MULTIPART_REPORT + "; report-type=disposition-notification; boundary=\"" + boundary + "\"";
    }

    protected TextPlainEntity buildPlainTextReport(HttpEntityEnclosingRequest request,
                                                   HttpResponse response,
                                                   DispositionMode dispositionMode,
                                                   AS2DispositionType dispositionType,
                                                   AS2DispositionModifier dispositionModifier,
                                                   String[] failureFields,
                                                   String[] errorFields,
                                                   String[] warningFields,
                                                   Map<String, String> extensionFields,
                                                   String charset)
            throws HttpException {

        String mdnDescription = createMdnDescription(request, response, dispositionMode, dispositionType,
                dispositionModifier, failureFields, errorFields, warningFields, extensionFields, charset);

        return new TextPlainEntity(mdnDescription, AS2Charset.US_ASCII, AS2TransferEncoding.SEVENBIT, false);
    }

    protected String getContentTypeValue(String boundary) {
        ContentType contentType = ContentType.parse(AS2MimeType.MULTIPART_REPORT + ";"
                + "report-type=disposition-notification; boundary=\"" + boundary + "\"");
        return contentType.toString();
    }
    
    private String createMdnDescription(HttpEntityEnclosingRequest request,
                                        HttpResponse response,
                                        DispositionMode dispositionMode,
                                        AS2DispositionType dispositionType,
                                        AS2DispositionModifier dispositionModifier,
                                        String[] failureFields,
                                        String[] errorFields,
                                        String[] warningFields,
                                        Map<String, String> extensionFields,
                                        String charset) throws HttpException {
        
        try {
            Context context = new VelocityContext();
            context.put("request", request);
            Map<String, Object> requestHeaders = new HashMap<>();
            for (Header header: request.getAllHeaders()) {
                requestHeaders.put(header.getName(), header.getValue());
            }
            context.put("requestHeaders", requestHeaders);
            
            Map<String, Object> responseHeaders = new HashMap<>();
            for (Header header: response.getAllHeaders()) {
                responseHeaders.put(header.getName(), header.getValue());
            }
            context.put("responseHeaders", responseHeaders);
            
            context.put("dispositionMode", dispositionMode);
            context.put("dispositionType", dispositionType);
            context.put("dispositionModifier", dispositionModifier);
            context.put("failureFields", failureFields);
            context.put("errorFields", errorFields);
            context.put("warningFields", warningFields);
            context.put("extensionFields", extensionFields);
            
            Template template = getVelocityEngine().getTemplate("mdnDescription.vm", charset);
            StringWriter sw = new StringWriter();
            template.merge(context, sw);
            
            return sw.toString();
        } catch (Exception e) {
            throw new HttpException("failed to create MDN description", e);
        }
    }
    
    private synchronized VelocityEngine getVelocityEngine() throws Exception {
        if (velocityEngine == null) {
            velocityEngine = new VelocityEngine();
            
            // set default properties
            Properties properties = new Properties();
            properties.setProperty(RuntimeConstants.RESOURCE_LOADER, "class");
            properties.setProperty("class.resource.loader.description", "Camel Velocity Classpath Resource Loader");
            properties.setProperty("class.resource.loader.class", ClasspathResourceLoader.class.getName());
            final Logger velocityLogger = LoggerFactory.getLogger("org.apache.camel.maven.Velocity");
            properties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, velocityLogger.getName());
            
            velocityEngine.init(properties);
        }
        return velocityEngine;
    }


}
