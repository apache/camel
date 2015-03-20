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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

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
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.util.StringUtil;

public class DefaultBulkApiClient extends AbstractClientBase implements BulkApiClient {

    private static final String TOKEN_HEADER = "X-SFDC-Session";
    private static final ContentType DEFAULT_ACCEPT_TYPE = ContentType.XML;

    private JAXBContext context;
    private ObjectFactory objectFactory;

    public DefaultBulkApiClient(String version, SalesforceSession session, HttpClient httpClient)
        throws SalesforceException {
        super(version, session, httpClient);

        try {
            context = JAXBContext.newInstance(JobInfo.class.getPackage().getName(), getClass().getClassLoader());
        } catch (JAXBException e) {
            String msg = "Error loading Bulk API DTOs: " + e.getMessage();
            throw new IllegalArgumentException(msg, e);
        }

        this.objectFactory = new ObjectFactory();
    }

    @Override
    public void createJob(JobInfo request, final JobInfoResponseCallback callback) {
        // clear system fields if set
        sanitizeJobRequest(request);

        final ContentExchange post = getContentExchange(HttpMethods.POST, jobUrl(null));
        try {
            marshalRequest(objectFactory.createJobInfo(request), post, APPLICATION_XML_UTF8);
        } catch (SalesforceException e) {
            callback.onResponse(null, e);
            return;
        }

        // make the call and parse the result in callback
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                JobInfo value = null;
                if (response != null) {
                    try {
                        value = unmarshalResponse(response, post, JobInfo.class);
                    } catch (SalesforceException e) {
                        ex = e;
                    }
                }
                callback.onResponse(value, ex);
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
    public void getJob(String jobId, final JobInfoResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, jobUrl(jobId));

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                JobInfo value = null;
                try {
                    value = unmarshalResponse(response, get, JobInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, ex);
            }
        });
    }

    @Override
    public void closeJob(String jobId, final JobInfoResponseCallback callback) {
        final JobInfo request = new JobInfo();
        request.setState(JobStateEnum.CLOSED);

        final ContentExchange post = getContentExchange(HttpMethods.POST, jobUrl(jobId));
        try {
            marshalRequest(objectFactory.createJobInfo(request), post, APPLICATION_XML_UTF8);
        } catch (SalesforceException e) {
            callback.onResponse(null, e);
            return;
        }

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                JobInfo value = null;
                try {
                    value = unmarshalResponse(response, post, JobInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, ex);
            }
        });
    }

    @Override
    public void abortJob(String jobId, final JobInfoResponseCallback callback) {
        final JobInfo request = new JobInfo();
        request.setState(JobStateEnum.ABORTED);

        final ContentExchange post = getContentExchange(HttpMethods.POST, jobUrl(jobId));
        try {
            marshalRequest(objectFactory.createJobInfo(request), post, APPLICATION_XML_UTF8);
        } catch (SalesforceException e) {
            callback.onResponse(null, e);
            return;
        }

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                JobInfo value = null;
                try {
                    value = unmarshalResponse(response, post, JobInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, ex);
            }
        });
    }

    @Override
    public void createBatch(InputStream batchStream, String jobId, ContentType contentTypeEnum, 
        final BatchInfoResponseCallback callback) {
        final ContentExchange post = getContentExchange(HttpMethods.POST, batchUrl(jobId, null));
        post.setRequestContentSource(batchStream);
        post.setRequestContentType(getContentType(contentTypeEnum) + ";charset=" + StringUtil.__UTF8);

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                BatchInfo value = null;
                try {
                    value = unmarshalResponse(response, post, BatchInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, ex);
            }
        });
    }

    @Override
    public void getBatch(String jobId, String batchId, final BatchInfoResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, batchUrl(jobId, batchId));

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                BatchInfo value = null;
                try {
                    value = unmarshalResponse(response, get, BatchInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, ex);
            }
        });
    }

    @Override
    public void getAllBatches(String jobId, final BatchInfoListResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, batchUrl(jobId, null));

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                BatchInfoList value = null;
                try {
                    value = unmarshalResponse(response, get, BatchInfoList.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value != null ? value.getBatchInfo() : null, ex);
            }
        });
    }

    @Override
    public void getRequest(String jobId, String batchId, final StreamResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, batchUrl(jobId, batchId));

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                callback.onResponse(response, ex);
            }
        });
    }

    @Override
    public void getResults(String jobId, String batchId, final StreamResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, batchResultUrl(jobId, batchId, null));

        // make the call and return the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                callback.onResponse(response, ex);
            }
        });
    }

    @Override
    public void createBatchQuery(String jobId, String soqlQuery, ContentType jobContentType,
        final BatchInfoResponseCallback callback) {
        final ContentExchange post = getContentExchange(HttpMethods.POST, batchUrl(jobId, null));
        byte[] queryBytes = soqlQuery.getBytes(StringUtil.__UTF8_CHARSET);
        post.setRequestContent(new ByteArrayBuffer(queryBytes));
        post.setRequestContentType(getContentType(jobContentType) + ";charset=" + StringUtil.__UTF8);

        // make the call and parse the result
        doHttpRequest(post, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                BatchInfo value = null;
                try {
                    value = unmarshalResponse(response, post, BatchInfo.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value, ex);
            }
        });
    }

    @Override
    public void getQueryResultIds(String jobId, String batchId, final QueryResultIdsCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, batchResultUrl(jobId, batchId, null));

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                QueryResultList value = null;
                try {
                    value = unmarshalResponse(response, get, QueryResultList.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(value != null ? Collections.unmodifiableList(value.getResult()) : null, ex);
            }
        });
    }

    @Override
    public void getQueryResult(String jobId, String batchId, String resultId, final StreamResponseCallback callback) {
        final ContentExchange get = getContentExchange(HttpMethods.GET, batchResultUrl(jobId, batchId, resultId));

        // make the call and parse the result
        doHttpRequest(get, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                callback.onResponse(response, ex);
            }
        });
    }

    @Override
    protected void setAccessToken(HttpExchange httpExchange) {
        httpExchange.setRequestHeader(TOKEN_HEADER, accessToken);
    }

    @Override
    protected void doHttpRequest(ContentExchange request, ClientResponseCallback callback) {
        // set access token for all requests
        setAccessToken(request);

        // set default charset
        request.setRequestHeader(HttpHeaders.ACCEPT_CHARSET, StringUtil.__UTF8);

        // TODO check if this is really needed or not, since SF response content type seems fixed
        // check if the default accept content type must be used
        if (!request.getRequestFields().containsKey(HttpHeaders.ACCEPT)) {
            final String contentType = getContentType(DEFAULT_ACCEPT_TYPE);
            request.setRequestHeader(HttpHeaders.ACCEPT, contentType);
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
    protected SalesforceException createRestException(ContentExchange request, String reason) {
        // this must be of type Error
        try {
            final Error error = unmarshalResponse(new ByteArrayInputStream(request.getResponseContentBytes()),
                    request, Error.class);

            final RestError restError = new RestError();
            restError.setErrorCode(error.getExceptionCode());
            restError.setMessage(error.getExceptionMessage());

            return new SalesforceException(Arrays.asList(restError), request.getResponseStatus());
        } catch (SalesforceException e) {
            String msg = "Error un-marshaling Salesforce Error: " + e.getMessage();
            return new SalesforceException(msg, e);
        }
    }

    private <T> T unmarshalResponse(InputStream response, ContentExchange request, Class<T> resultClass)
        throws SalesforceException {
        try {
            Unmarshaller unmarshaller = context.createUnmarshaller();
            JAXBElement<T> result = unmarshaller.unmarshal(new StreamSource(response), resultClass);
            return result.getValue();
        } catch (JAXBException e) {
            throw new SalesforceException(
                    String.format("Error unmarshaling response {%s:%s} : %s",
                            request.getMethod(), request.getRequestURI(), e.getMessage()),
                    e);
        } catch (IllegalArgumentException e) {
            throw new SalesforceException(
                    String.format("Error unmarshaling response for {%s:%s} : %s",
                            request.getMethod(), request.getRequestURI(), e.getMessage()),
                    e);
        }
    }

    private void marshalRequest(Object input, ContentExchange request, String contentType) throws SalesforceException {
        try {
            Marshaller marshaller = context.createMarshaller();
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            marshaller.marshal(input, byteStream);
            request.setRequestContent(new ByteArrayBuffer(byteStream.toByteArray()));
            request.setRequestContentType(contentType);
        } catch (JAXBException e) {
            throw new SalesforceException(
                    String.format("Error marshaling request for {%s:%s} : %s",
                            request.getMethod(), request.getRequestURI(), e.getMessage()),
                    e);
        } catch (IllegalArgumentException e) {
            throw new SalesforceException(
                    String.format("Error marshaling request for {%s:%s} : %s",
                            request.getMethod(), request.getRequestURI(), e.getMessage()),
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
}
