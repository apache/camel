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
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import com.thoughtworks.xstream.XStream;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.SalesforceMultipleChoicesException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.dto.RestChoices;
import org.apache.camel.component.salesforce.internal.dto.RestErrors;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.StringUtil;

public class DefaultRestClient extends AbstractClientBase implements RestClient {

    private static final String SERVICES_DATA = "/services/data/";
    private static final String TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String SERVICES_APEXREST = "/services/apexrest/";

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
        xStream.processAnnotations(RestChoices.class);

        XStreamUtils.addDefaultPermissions(xStream);
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
    protected SalesforceException createRestException(ContentExchange httpExchange, String reason) {
        // get status code and reason phrase
        final int statusCode = httpExchange.getResponseStatus();
        if (reason == null || reason.isEmpty()) {
            reason = HttpStatus.getMessage(statusCode);
        }
        // try parsing response according to format
        String responseContent = null;
        try {
            responseContent = httpExchange.getResponseContent();
            if (responseContent != null && !responseContent.isEmpty()) {
                final List<String> choices;
                // return list of choices as error message for 300
                if (statusCode == HttpStatus.MULTIPLE_CHOICES_300) {
                    if (PayloadFormat.JSON.equals(format)) {
                        choices = objectMapper.readValue(
                            responseContent, new TypeReference<List<String>>() {
                            }
                        );
                    } else {
                        RestChoices restChoices = new RestChoices();
                        xStream.fromXML(responseContent, restChoices);
                        choices = restChoices.getUrls();
                    }
                    return new SalesforceMultipleChoicesException(reason, statusCode, choices);
                } else {
                    final List<RestError> restErrors;
                    if (PayloadFormat.JSON.equals(format)) {
                        restErrors = objectMapper.readValue(
                            responseContent, new TypeReference<List<RestError>>() {
                            }
                        );
                    } else {
                        RestErrors errors = new RestErrors();
                        xStream.fromXML(responseContent, errors);
                        restErrors = errors.getErrors();
                    }
                    return new SalesforceException(restErrors, statusCode);
                }
            }
        } catch (IOException e) {
            // log and ignore
            String msg = "Unexpected Error parsing " + format
                    + " error response body + [" + responseContent + "] : " + e.getMessage();
            log.warn(msg, e);
        } catch (RuntimeException e) {
            // log and ignore
            String msg = "Unexpected Error parsing " + format
                    + " error response body + [" + responseContent + "] : " + e.getMessage();
            log.warn(msg, e);
        }

        // just report HTTP status info
        return new SalesforceException("Unexpected error: " + reason + ", with content: " + responseContent,
                statusCode);
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

            String encodedQuery = urlEncode(soqlQuery);
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

            String encodedQuery = urlEncode(soslQuery);
            final ContentExchange get = getContentExchange(HttpMethods.GET, versionUrl() + "search/?q=" + encodedQuery);

            // requires authorization token
            setAccessToken(get);

            doHttpRequest(get, new DelegatingClientCallback(callback));

        } catch (UnsupportedEncodingException e) {
            String msg = "Unexpected error: " + e.getMessage();
            callback.onResponse(null, new SalesforceException(msg, e));
        }
    }

    @Override
    public void apexCall(String httpMethod, String apexUrl,
                         Map<String, Object> queryParams, InputStream requestDto, ResponseCallback callback) {
        // create APEX call exchange
        final ContentExchange exchange;
        try {
            exchange = getContentExchange(httpMethod, apexCallUrl(apexUrl, queryParams));
            // set request SObject and content type
            if (requestDto != null) {
                exchange.setRequestContentSource(requestDto);
                exchange.setRequestContentType(
                    PayloadFormat.JSON.equals(format) ? APPLICATION_JSON_UTF8 : APPLICATION_XML_UTF8);
            }

            // requires authorization token
            setAccessToken(exchange);

            doHttpRequest(exchange, new DelegatingClientCallback(callback));
        } catch (UnsupportedEncodingException e) {
            String msg = "Unexpected error: " + e.getMessage();
            callback.onResponse(null, new SalesforceException(msg, e));
        } catch (URISyntaxException e) {
            String msg = "Unexpected error: " + e.getMessage();
            callback.onResponse(null, new SalesforceException(msg, e));
        }
    }

    private String apexCallUrl(String apexUrl, Map<String, Object> queryParams)
        throws UnsupportedEncodingException, URISyntaxException {

        if (queryParams != null && !queryParams.isEmpty()) {
            apexUrl = URISupport.appendParametersToURI(apexUrl, queryParams);
        }

        return instanceUrl + SERVICES_APEXREST + apexUrl;
    }

    private String servicesDataUrl() {
        return instanceUrl + SERVICES_DATA;
    }

    private String versionUrl() {
        ObjectHelper.notNull(version, "version");
        return servicesDataUrl() + "v" + version + "/";
    }

    private String sobjectsUrl(String sObjectName) {
        ObjectHelper.notNull(sObjectName, "sObjectName");
        return versionUrl() + "sobjects/" + sObjectName;
    }

    private String sobjectsExternalIdUrl(String sObjectName, String fieldName, String fieldValue) {
        if (fieldName == null || fieldValue == null) {
            throw new IllegalArgumentException("External field name and value cannot be NULL");
        }
        try {
            String encodedValue = urlEncode(fieldValue);
            return sobjectsUrl(sObjectName + "/" + fieldName + "/" + encodedValue);
        } catch (UnsupportedEncodingException e) {
            String msg = "Unexpected error: " + e.getMessage();
            throw new IllegalArgumentException(msg, e);
        }
    }

    protected void setAccessToken(HttpExchange httpExchange) {
        httpExchange.setRequestHeader(TOKEN_HEADER, TOKEN_PREFIX + accessToken);
    }

    private String urlEncode(String query) throws UnsupportedEncodingException {
        String encodedQuery = URLEncoder.encode(query, StringUtil.__UTF8_CHARSET.toString());
        // URLEncoder likes to use '+' for spaces
        encodedQuery = encodedQuery.replace("+", "%20");
        return encodedQuery;
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
