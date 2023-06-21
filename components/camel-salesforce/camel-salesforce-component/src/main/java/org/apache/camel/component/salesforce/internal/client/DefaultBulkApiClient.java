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
package org.apache.camel.component.salesforce.internal.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfoList;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.Error;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulk.ObjectFactory;
import org.apache.camel.component.salesforce.api.dto.bulk.QueryResultList;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.StringUtil;

public class DefaultBulkApiClient extends AbstractClientBase implements BulkApiClient {

    private static final String TOKEN_HEADER = "X-SFDC-Session";
    private static final ContentType DEFAULT_ACCEPT_TYPE = ContentType.XML;

    private JAXBContext context;
    private ObjectFactory objectFactory;

    public DefaultBulkApiClient(String version, SalesforceSession session, SalesforceHttpClient httpClient,
                                SalesforceLoginConfig loginConfig) throws SalesforceException {
        super(version, session, httpClient, loginConfig);

        try {
            context = JAXBContext.newInstance(JobInfo.class.getPackage().getName(), getClass().getClassLoader());
        } catch (JAXBException e) {
            String msg = "Error loading Bulk API DTOs: " + e.getMessage();
            throw new IllegalArgumentException(msg, e);
        }

        this.objectFactory = new ObjectFactory();
    }

    @Override
    public void createJob(JobInfo request, Map<String, List<String>> headers, final JobInfoResponseCallback callback) {
        // clear system fields if set
        sanitizeJobRequest(request);

        final Request post = getRequest(HttpMethod.POST, jobUrl(null), headers);
        try {
            marshalRequest(objectFactory.createJobInfo(request), post, APPLICATION_XML_UTF8);
        } catch (Exception e) {
            callback.onResponse(null, Collections.emptyMap(), new SalesforceException(e));
            return;
        }

        // make the call and parse the result in callback
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                JobInfo value = null;
                if (response != null) {
                    try {
                        value = unmarshalResponse(response, post, JobInfo.class);
                    } catch (SalesforceException e) {
                        ex = e;
                    }
                }
                callback.onResponse(value, headers, ex);
            }
        });

    }

    // reset read only fields
    private void sanitizeJobRequest(JobInfo request) {
        request.setApexProcessingTime(null);
        request.setApiActiveProcessingTime(null);
        request.setApiVersion(null);
        request.setCreatedById(null);
        request.setCreatedDate(null);
        request.setId(null);
        request.setNumberBatchesCompleted(null);
        request.setNumberBatchesFailed(null);
        request.setNumberBatchesInProgress(null);
        request.setNumberBatchesQueued(null);
        request.setNumberBatchesTotal(null);
        request.setNumberRecordsFailed(null);
        request.setNumberRecordsProcessed(null);
        request.setNumberRetries(null);
        request.setState(null);
        request.setSystemModstamp(null);
        request.setSystemModstamp(null);
    }

    @Override
    public void getJob(String jobId, Map<String, List<String>> headers, final JobInfoResponseCallback callback) {
        final Request get = getRequest(HttpMethod.GET, jobUrl(jobId), headers);

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                JobInfo value = null;
                try {
                    value = unmarshalResponse(response, get, JobInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, headers, ex);
            }
        });
    }

    @Override
    public void closeJob(String jobId, Map<String, List<String>> headers, final JobInfoResponseCallback callback) {
        final JobInfo request = new JobInfo();
        request.setState(JobStateEnum.CLOSED);

        final Request post = getRequest(HttpMethod.POST, jobUrl(jobId), headers);
        try {
            marshalRequest(objectFactory.createJobInfo(request), post, APPLICATION_XML_UTF8);
        } catch (SalesforceException e) {
            callback.onResponse(null, Collections.emptyMap(), e);
            return;
        }

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                JobInfo value = null;
                try {
                    value = unmarshalResponse(response, post, JobInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, headers, ex);
            }
        });
    }

    @Override
    public void abortJob(String jobId, Map<String, List<String>> headers, final JobInfoResponseCallback callback) {
        final JobInfo request = new JobInfo();
        request.setState(JobStateEnum.ABORTED);

        final Request post = getRequest(HttpMethod.POST, jobUrl(jobId), headers);
        try {
            marshalRequest(objectFactory.createJobInfo(request), post, APPLICATION_XML_UTF8);
        } catch (SalesforceException e) {
            callback.onResponse(null, Collections.emptyMap(), e);
            return;
        }

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                JobInfo value = null;
                try {
                    value = unmarshalResponse(response, post, JobInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, headers, ex);
            }
        });
    }

    @Override
    public void createBatch(
            InputStream batchStream, String jobId, ContentType contentTypeEnum, Map<String, List<String>> headers,
            final BatchInfoResponseCallback callback) {
        final Request post = getRequest(HttpMethod.POST, batchUrl(jobId, null), headers);
        post.content(new InputStreamContentProvider(batchStream));
        post.header(HttpHeader.CONTENT_TYPE, getContentType(contentTypeEnum) + ";charset=" + StringUtil.__UTF8);

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                BatchInfo value = null;
                try {
                    value = unmarshalResponse(response, post, BatchInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, headers, ex);
            }
        });
    }

    @Override
    public void getBatch(
            String jobId, String batchId, Map<String, List<String>> headers, final BatchInfoResponseCallback callback) {
        final Request get = getRequest(HttpMethod.GET, batchUrl(jobId, batchId), headers);

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                BatchInfo value = null;
                try {
                    value = unmarshalResponse(response, get, BatchInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, headers, ex);
            }
        });
    }

    @Override
    public void getAllBatches(String jobId, Map<String, List<String>> headers, final BatchInfoListResponseCallback callback) {
        final Request get = getRequest(HttpMethod.GET, batchUrl(jobId, null), headers);

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                BatchInfoList value = null;
                try {
                    value = unmarshalResponse(response, get, BatchInfoList.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value != null ? value.getBatchInfo() : null, headers, ex);
            }
        });
    }

    @Override
    public void getRequest(
            String jobId, String batchId, Map<String, List<String>> headers, final StreamResponseCallback callback) {
        final Request get = getRequest(HttpMethod.GET, batchRequestUrl(jobId, batchId, null), headers);

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(response, headers, ex);
            }
        });
    }

    @Override
    public void getResults(
            String jobId, String batchId, Map<String, List<String>> headers, final StreamResponseCallback callback) {
        final Request get = getRequest(HttpMethod.GET, batchResultUrl(jobId, batchId, null), headers);

        // make the call and return the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(response, headers, ex);
            }
        });
    }

    @Override
    public void createBatchQuery(
            String jobId, String soqlQuery, ContentType jobContentType, Map<String, List<String>> headers,
            final BatchInfoResponseCallback callback) {
        final Request post = getRequest(HttpMethod.POST, batchUrl(jobId, null), headers);
        final byte[] queryBytes;
        try {
            queryBytes = soqlQuery.getBytes(StringUtil.__UTF8);
        } catch (UnsupportedEncodingException e) {
            callback.onResponse(null, Collections.emptyMap(),
                    new SalesforceException("Unexpected exception: " + e.getMessage(), e));
            return;
        }
        post.content(new BytesContentProvider(queryBytes));
        post.header(HttpHeader.CONTENT_TYPE, getContentType(jobContentType) + ";charset=" + StringUtil.__UTF8);

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                BatchInfo value = null;
                try {
                    value = unmarshalResponse(response, post, BatchInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, headers, ex);
            }
        });
    }

    @Override
    public void getQueryResultIds(
            String jobId, String batchId, Map<String, List<String>> headers, final QueryResultIdsCallback callback) {
        final Request get = getRequest(HttpMethod.GET, batchResultUrl(jobId, batchId, null), headers);

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                QueryResultList value = null;
                try {
                    value = unmarshalResponse(response, get, QueryResultList.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value != null ? Collections.unmodifiableList(value.getResult()) : null, headers, ex);
            }
        });
    }

    @Override
    public void getQueryResult(
            String jobId, String batchId, String resultId, Map<String, List<String>> headers,
            final StreamResponseCallback callback) {
        final Request get = getRequest(HttpMethod.GET, batchResultUrl(jobId, batchId, resultId), headers);

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(response, headers, ex);
            }
        });
    }

    @Override
    protected void setAccessToken(Request request) {
        // replace old token
        request.header(TOKEN_HEADER, null);
        request.header(TOKEN_HEADER, accessToken);
    }

    @Override
    protected void doHttpRequest(Request request, ClientResponseCallback callback) {
        // set access token for all requests
        setAccessToken(request);

        // set default charset
        request.header(HttpHeader.ACCEPT_CHARSET, StringUtil.__UTF8);

        // TODO check if this is really needed or not, since SF response content
        // type seems fixed
        // check if the default accept content type must be used
        if (!request.getHeaders().contains(HttpHeader.ACCEPT)) {
            final String contentType = getContentType(DEFAULT_ACCEPT_TYPE);
            request.header(HttpHeader.ACCEPT, contentType);
            // request content type and charset is set by the request entity
        }

        super.doHttpRequest(request, callback);
    }

    private static String getContentType(ContentType type) {
        String result = null;

        switch (type) {
            case CSV:
                result = "text/csv";
                break;

            case XML:
                result = "application/xml";
                break;

            case ZIP_CSV:
            case ZIP_XML:
                result = type.toString().toLowerCase().replace('_', '/');
                break;

            default:
                break;
        }

        return result;
    }

    @Override
    protected SalesforceException createRestException(Response response, InputStream responseContent) {
        // this must be of type Error
        try {
            final Error error = unmarshalResponse(responseContent, response.getRequest(), Error.class);

            final RestError restError = new RestError();
            restError.setErrorCode(error.getExceptionCode());
            restError.setMessage(error.getExceptionMessage());

            return new SalesforceException(Arrays.asList(restError), response.getStatus());
        } catch (SalesforceException e) {
            String msg = "Error un-marshaling Salesforce Error: " + e.getMessage();
            return new SalesforceException(msg, e);
        }
    }

    private <T> T unmarshalResponse(InputStream response, Request request, Class<T> resultClass) throws SalesforceException {
        try {
            Unmarshaller unmarshaller = context.createUnmarshaller();

            // Disable XXE
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            try {
                spf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
                spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (ParserConfigurationException | SAXException ex) {
                // LOG.debug("Error setting feature on parser: " +
                // ex.getMessage());
            }
            Source xmlSource = new SAXSource(spf.newSAXParser().getXMLReader(), new InputSource(response));

            JAXBElement<T> result = unmarshaller.unmarshal(xmlSource, resultClass);
            return result.getValue();
        } catch (JAXBException | SAXException | ParserConfigurationException e) {
            throw new SalesforceException(
                    String.format("Error unmarshaling response {%s:%s} : %s", request.getMethod(), request.getURI(),
                            e.getMessage()),
                    e);
        } catch (Exception e) {
            throw new SalesforceException(
                    String.format("Error unmarshaling response for {%s:%s} : %s", request.getMethod(), request.getURI(),
                            e.getMessage()),
                    e);
        }
    }

    private void marshalRequest(Object input, Request request, String contentType) throws SalesforceException {
        try {
            Marshaller marshaller = context.createMarshaller();
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            marshaller.marshal(input, byteStream);

            request.content(new BytesContentProvider(contentType, byteStream.toByteArray()));
        } catch (Exception e) {
            throw new SalesforceException(
                    String.format("Error marshaling request for {%s:%s} : %s", request.getMethod(), request.getURI(),
                            e.getMessage()),
                    e);
        }
    }

    private String jobUrl(String jobId) {
        if (jobId != null) {
            return super.instanceUrl + "/services/async/" + version + "/job/" + jobId;
        } else {
            return super.instanceUrl + "/services/async/" + version + "/job";
        }
    }

    private String batchUrl(String jobId, String batchId) {
        if (batchId != null) {
            return jobUrl(jobId) + "/batch/" + batchId;
        } else {
            return jobUrl(jobId) + "/batch";
        }
    }

    private String batchResultUrl(String jobId, String batchId, String resultId) {
        if (resultId != null) {
            return batchUrl(jobId, batchId) + "/result/" + resultId;
        } else {
            return batchUrl(jobId, batchId) + "/result";
        }
    }

    private String batchRequestUrl(String jobId, String batchId, String requestId) {
        if (requestId != null) {
            return batchUrl(jobId, batchId) + "/request/" + requestId;
        } else {
            return batchUrl(jobId, batchId) + "/request";
        }
    }
}
