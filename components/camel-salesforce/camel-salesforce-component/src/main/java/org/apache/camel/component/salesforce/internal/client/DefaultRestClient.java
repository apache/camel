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
package org.apache.camel.component.salesforce.internal.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import com.thoughtworks.xstream.XStream;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.dto.RestErrors;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.util.StringUtil;

public class DefaultRestClient extends AbstractClientBase implements RestClient {

    private static final String SERVICES_DATA = "/services/data/";
    private static final String TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    protected PayloadFormat format;
    private ObjectMapper objectMapper;
    private XStream xStream;

    public DefaultRestClient(HttpClient httpClient, String version, PayloadFormat format, SalesforceSession session)
        throws SalesforceException {
        super(version, session, httpClient);

        this.format = format;

        // initialize error parsers for JSON and XML
        this.objectMapper = new ObjectMapper();
        this.xStream = new XStream();
        xStream.processAnnotations(RestErrors.class);
    }

    @Override
    protected void doHttpRequest(ContentExchange request, ClientResponseCallback callback) {
        // set standard headers for all requests
        final String contentType = PayloadFormat.JSON.equals(format) ? APPLICATION_JSON_UTF8 : APPLICATION_XML_UTF8;
        request.setRequestHeader(HttpHeaders.ACCEPT, contentType);
        request.setRequestHeader(HttpHeaders.ACCEPT_CHARSET, StringUtil.__UTF8);
        // request content type and charset is set by the request entity

        super.doHttpRequest(request, callback);
    }

    @Override
    protected SalesforceException createRestException(ContentExchange httpExchange) {
        // try parsing response according to format
        try {
            if (PayloadFormat.JSON.equals(format)) {
                List<RestError> restErrors = objectMapper.readValue(
                    httpExchange.getResponseContent(), new TypeReference<List<RestError>>() {
                    }
                );
                return new SalesforceException(restErrors, httpExchange.getResponseStatus());
            } else {
                RestErrors errors = new RestErrors();
                xStream.fromXML(httpExchange.getResponseContent(), errors);
                return new SalesforceException(errors.getErrors(), httpExchange.getResponseStatus());
            }
        } catch (IOException e) {
            // log and ignore
            String msg = "Unexpected Error parsing " + format + " error response: " + e.getMessage();
            log.warn(msg, e);
        } catch (RuntimeException e) {
            // log and ignore
            String msg = "Unexpected Error parsing " + format + " error response: " + e.getMessage();
            log.warn(msg, e);
        }

        // just report HTTP status info
        return new SalesforceException("Unexpected error", httpExchange.getResponseStatus());
    }

    @Override
    public void getVersions(final ResponseCallback callback) {
        ContentExchange get = getContentExchange(HttpMethods.GET, servicesDataUrl());
        // does not require authorization token

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void getResources(ResponseCallback callback) {
        ContentExchange get = getContentExchange(HttpMethods.GET, versionUrl());
        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void getGlobalObjects(ResponseCallback callback) {
        ContentExchange get = getContentExchange(HttpMethods.GET, sobjectsUrl(""));
        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void getBasicInfo(String sObjectName,
                             ResponseCallback callback) {
        ContentExchange get = getContentExchange(HttpMethods.GET, sobjectsUrl(sObjectName + "/"));
        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void getDescription(String sObjectName,
                               ResponseCallback callback) {
        ContentExchange get = getContentExchange(HttpMethods.GET, sobjectsUrl(sObjectName + "/describe/"));
        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void getSObject(String sObjectName, String id, String[] fields,
                           ResponseCallback callback) {

        // parse fields if set
        String params = "";
        if (fields != null && fields.length > 0) {
            StringBuilder fieldsValue = new StringBuilder("?fields=");
            for (int i = 0; i < fields.length; i++) {
                fieldsValue.append(fields[i]);
                if (i < (fields.length - 1)) {
                    fieldsValue.append(',');
                }
            }
            params = fieldsValue.toString();
        }
        ContentExchange get = getContentExchange(HttpMethods.GET, sobjectsUrl(sObjectName + "/" + id + params));
        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void createSObject(String sObjectName, InputStream sObject,
                              ResponseCallback callback) {
        // post the sObject
        final ContentExchange post = getContentExchange(HttpMethods.POST, sobjectsUrl(sObjectName));

        // authorization
        setAccessToken(post);

        // input stream as entity content
        post.setRequestContentSource(sObject);
        post.setRequestContentType(PayloadFormat.JSON.equals(format) ? APPLICATION_JSON_UTF8 : APPLICATION_XML_UTF8);

        doHttpRequest(post, new DelegatingClientCallback(callback));
    }

    @Override
    public void updateSObject(String sObjectName, String id, InputStream sObject,
                              ResponseCallback callback) {
        final ContentExchange patch = getContentExchange("PATCH", sobjectsUrl(sObjectName + "/" + id));
        // requires authorization token
        setAccessToken(patch);

        // input stream as entity content
        patch.setRequestContentSource(sObject);
        patch.setRequestContentType(PayloadFormat.JSON.equals(format) ? APPLICATION_JSON_UTF8 : APPLICATION_XML_UTF8);

        doHttpRequest(patch, new DelegatingClientCallback(callback));
    }

    @Override
    public void deleteSObject(String sObjectName, String id,
                              ResponseCallback callback) {
        final ContentExchange delete = getContentExchange(HttpMethods.DELETE, sobjectsUrl(sObjectName + "/" + id));

        // requires authorization token
        setAccessToken(delete);

        doHttpRequest(delete, new DelegatingClientCallback(callback));
    }

    @Override
    public void getSObjectWithId(String sObjectName, String fieldName, String fieldValue,
                                 ResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET,
                sobjectsExternalIdUrl(sObjectName, fieldName, fieldValue));

        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void upsertSObject(String sObjectName, String fieldName, String fieldValue, InputStream sObject,
                              ResponseCallback callback) {
        final ContentExchange patch = getContentExchange("PATCH",
                sobjectsExternalIdUrl(sObjectName, fieldName, fieldValue));

        // requires authorization token
        setAccessToken(patch);

        // input stream as entity content
        patch.setRequestContentSource(sObject);
        // TODO will the encoding always be UTF-8??
        patch.setRequestContentType(PayloadFormat.JSON.equals(format) ? APPLICATION_JSON_UTF8 : APPLICATION_XML_UTF8);

        doHttpRequest(patch, new DelegatingClientCallback(callback));
    }

    @Override
    public void deleteSObjectWithId(String sObjectName, String fieldName, String fieldValue,
                                    ResponseCallback callback) {
        final ContentExchange delete = getContentExchange(HttpMethods.DELETE,
                sobjectsExternalIdUrl(sObjectName, fieldName, fieldValue));

        // requires authorization token
        setAccessToken(delete);

        doHttpRequest(delete, new DelegatingClientCallback(callback));
    }

    @Override
    public void getBlobField(String sObjectName, String id, String blobFieldName, ResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET,
                sobjectsUrl(sObjectName + "/" + id + "/" + blobFieldName));
        // TODO this doesn't seem to be required, the response is always the content binary stream
        //get.setRequestHeader(HttpHeaders.ACCEPT_ENCODING, "base64");

        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void query(String soqlQuery, ResponseCallback callback) {
        try {

            String encodedQuery = URLEncoder.encode(soqlQuery, StringUtil.__UTF8_CHARSET.toString());
            // URLEncoder likes to use '+' for spaces
            encodedQuery = encodedQuery.replace("+", "%20");
            final ContentExchange get = getContentExchange(HttpMethods.GET, versionUrl() + "query/?q=" + encodedQuery);

            // requires authorization token
            setAccessToken(get);

            doHttpRequest(get, new DelegatingClientCallback(callback));

        } catch (UnsupportedEncodingException e) {
            String msg = "Unexpected error: " + e.getMessage();
            callback.onResponse(null, new SalesforceException(msg, e));
        }
    }

    @Override
    public void queryMore(String nextRecordsUrl, ResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, instanceUrl + nextRecordsUrl);

        // requires authorization token
        setAccessToken(get);

        doHttpRequest(get, new DelegatingClientCallback(callback));
    }

    @Override
    public void search(String soslQuery, ResponseCallback callback) {
        try {

            String encodedQuery = URLEncoder.encode(soslQuery, StringUtil.__UTF8_CHARSET.toString());
            // URLEncoder likes to use '+' for spaces
            encodedQuery = encodedQuery.replace("+", "%20");
            final ContentExchange get = getContentExchange(HttpMethods.GET, versionUrl() + "search/?q=" + encodedQuery);

            // requires authorization token
            setAccessToken(get);

            doHttpRequest(get, new DelegatingClientCallback(callback));

        } catch (UnsupportedEncodingException e) {
            String msg = "Unexpected error: " + e.getMessage();
            callback.onResponse(null, new SalesforceException(msg, e));
        }
    }

    private String servicesDataUrl() {
        return instanceUrl + SERVICES_DATA;
    }

    private String versionUrl() {
        if (version == null) {
            throw new IllegalArgumentException("NULL API version", new NullPointerException("version"));
        }
        return servicesDataUrl() + "v" + version + "/";
    }

    private String sobjectsUrl(String sObjectName) {
        if (sObjectName == null) {
            throw new IllegalArgumentException("Null SObject name", new NullPointerException("sObjectName"));
        }
        return versionUrl() + "sobjects/" + sObjectName;
    }

    private String sobjectsExternalIdUrl(String sObjectName, String fieldName, String fieldValue) {
        if (fieldName == null || fieldValue == null) {
            throw new IllegalArgumentException("External field name and value cannot be NULL");
        }
        try {
            String encodedValue = URLEncoder.encode(fieldValue, StringUtil.__UTF8_CHARSET.toString());
            // URLEncoder likes to use '+' for spaces
            encodedValue = encodedValue.replace("+", "%20");
            return sobjectsUrl(sObjectName + "/" + fieldName + "/" + encodedValue);
        } catch (UnsupportedEncodingException e) {
            String msg = "Unexpected error: " + e.getMessage();
            throw new IllegalArgumentException(msg, e);
        }
    }

    protected void setAccessToken(HttpExchange httpExchange) {
        httpExchange.setRequestHeader(TOKEN_HEADER, TOKEN_PREFIX + accessToken);
    }

    private static class DelegatingClientCallback implements ClientResponseCallback {
        private final ResponseCallback callback;

        public DelegatingClientCallback(ResponseCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onResponse(InputStream response, SalesforceException ex) {
            callback.onResponse(response, ex);
        }
    }

}
