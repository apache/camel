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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.client.DefaultRestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.util.ServiceHelper;

import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.API_VERSION;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_BLOB_FIELD_NAME;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_CLASS;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_EXT_ID_NAME;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_FIELDS;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_NAME;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_QUERY;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_SEARCH;

public abstract class AbstractRestProcessor extends AbstractSalesforceProcessor {

    protected static final String RESPONSE_CLASS = AbstractRestProcessor.class.getName() + ".responseClass";

    private RestClient restClient;
    private Map<String, Class<?>> classMap;

    public AbstractRestProcessor(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

        final PayloadFormat payloadFormat = endpoint.getConfiguration().getPayloadFormat();

        this.restClient = new DefaultRestClient(httpClient, endpointConfigMap.get(API_VERSION),
                payloadFormat.toString().toLowerCase(), session);

        this.classMap = endpoint.getComponent().getClassMap();
    }

    @Override
    public void start() throws Exception {
        ServiceHelper.startService(restClient);
    }

    @Override
    public void stop() throws Exception {
        ServiceHelper.stopService(restClient);
    }

    @Override
    public final boolean process(final Exchange exchange, final AsyncCallback callback) {

        // pre-process request message
        try {
            processRequest(exchange);
        } catch (SalesforceException e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        } catch (RuntimeException e) {
            exchange.setException(new SalesforceException(e.getMessage(), e));
            callback.done(true);
            return true;
        }

        // call Salesforce asynchronously
        try {

            // call Operation using REST client
            switch (operationName) {
                case GET_VERSIONS:
                    restClient.getVersions(new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            // process response entity and create out message
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

                case GET_RESOURCES:
                    restClient.getResources(new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

                case GET_GLOBAL_OBJECTS:
                    restClient.getGlobalObjects(new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

                case GET_BASIC_INFO:
                    String sObjectName = getParameter(SOBJECT_NAME, exchange, USE_BODY, NOT_OPTIONAL);
                    restClient.getBasicInfo(sObjectName, new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

                case GET_DESCRIPTION:
                    sObjectName = getParameter(SOBJECT_NAME, exchange, USE_BODY, NOT_OPTIONAL);
                    restClient.getDescription(sObjectName, new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

                case GET_SOBJECT: {
                    String sObjectIdValue;
                    // determine parameters from input AbstractSObject
                    final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                        sObjectIdValue = sObjectBase.getId();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                        sObjectIdValue = getParameter(SOBJECT_ID, exchange, USE_BODY, NOT_OPTIONAL);
                    }
                    final String sObjectId = sObjectIdValue;

                    // use sObject name to load class
                    setResponseClass(exchange, sObjectName);

                    // get optional field list
                    String fieldsValue = getParameter(SOBJECT_FIELDS, exchange, IGNORE_BODY, IS_OPTIONAL);
                    String[] fields = null;
                    if (fieldsValue != null) {
                        fields = fieldsValue.split(",");
                    }

                    restClient.getSObject(sObjectName, sObjectId, fields, new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                            restoreFields(exchange, sObjectBase, sObjectId, null, null);
                        }
                    });

                    break;
                }

                case CREATE_SOBJECT: {
                    // determine parameters from input AbstractSObject
                    AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                    }

                    restClient.createSObject(sObjectName, getRequestStream(exchange),
                            new RestClient.ResponseCallback() {
                                @Override
                                public void onResponse(InputStream response, SalesforceException exception) {
                                    processResponse(exchange, response, exception, callback);
                                }
                            });

                    break;
                }

                case UPDATE_SOBJECT: {
                    // determine parameters from input AbstractSObject
                    final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    String sObjectId;
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                        // remember the sObject Id
                        sObjectId = sObjectBase.getId();
                        // clear base object fields, which cannot be updated
                        sObjectBase.clearBaseFields();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                        sObjectId = getParameter(SOBJECT_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
                    }

                    final String finalsObjectId = sObjectId;
                    restClient.updateSObject(sObjectName, sObjectId, getRequestStream(exchange),
                            new RestClient.ResponseCallback() {
                                @Override
                                public void onResponse(InputStream response, SalesforceException exception) {
                                    processResponse(exchange, response, exception, callback);
                                    restoreFields(exchange, sObjectBase, finalsObjectId, null, null);
                                }
                            });

                    break;
                }

                case DELETE_SOBJECT: {
                    // determine parameters from input AbstractSObject
                    final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    String sObjectIdValue;
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                        sObjectIdValue = sObjectBase.getId();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                        sObjectIdValue = getParameter(SOBJECT_ID, exchange, USE_BODY, NOT_OPTIONAL);
                    }
                    final String sObjectId = sObjectIdValue;

                    restClient.deleteSObject(sObjectName, sObjectId, new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                            restoreFields(exchange, sObjectBase, sObjectId, null, null);
                        }
                    });
                    break;
                }

                case GET_SOBJECT_WITH_ID: {
                    Object oldValue = null;
                    String sObjectExtIdValue;
                    final String sObjectExtIdName = getParameter(SOBJECT_EXT_ID_NAME,
                            exchange, IGNORE_BODY, NOT_OPTIONAL);

                    // determine parameters from input AbstractSObject
                    final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                        oldValue = getAndClearPropertyValue(sObjectBase, sObjectExtIdName);
                        sObjectExtIdValue = oldValue.toString();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                        sObjectExtIdValue = getParameter(SOBJECT_EXT_ID_VALUE, exchange, USE_BODY, NOT_OPTIONAL);
                    }

                    // use sObject name to load class
                    setResponseClass(exchange, sObjectName);

                    final Object finalOldValue = oldValue;
                    restClient.getSObjectWithId(sObjectName, sObjectExtIdName, sObjectExtIdValue,
                            new RestClient.ResponseCallback() {
                                @Override
                                public void onResponse(InputStream response, SalesforceException exception) {
                                    processResponse(exchange, response, exception, callback);
                                    restoreFields(exchange, sObjectBase, null, sObjectExtIdName, finalOldValue);
                                }
                            });

                    break;
                }

                case UPSERT_SOBJECT: {
                    String sObjectExtIdValue;
                    final String sObjectExtIdName = getParameter(SOBJECT_EXT_ID_NAME, exchange,
                            IGNORE_BODY, NOT_OPTIONAL);

                    // determine parameters from input AbstractSObject
                    Object oldValue = null;
                    final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                        oldValue = getAndClearPropertyValue(sObjectBase, sObjectExtIdName);
                        sObjectExtIdValue = oldValue.toString();
                        // clear base object fields, which cannot be updated
                        sObjectBase.clearBaseFields();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                        sObjectExtIdValue = getParameter(SOBJECT_EXT_ID_VALUE, exchange, IGNORE_BODY, NOT_OPTIONAL);
                    }

                    final Object finalOldValue = oldValue;
                    restClient.upsertSObject(sObjectName, sObjectExtIdName, sObjectExtIdValue,
                            getRequestStream(exchange), new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                            restoreFields(exchange, sObjectBase, null, sObjectExtIdName, finalOldValue);
                        }
                    });

                    break;
                }

                case DELETE_SOBJECT_WITH_ID: {
                    final String sObjectExtIdName = getParameter(SOBJECT_EXT_ID_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);

                    // determine parameters from input AbstractSObject
                    Object oldValue = null;
                    final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    String sObjectExtIdValue;
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                        oldValue = getAndClearPropertyValue(sObjectBase, sObjectExtIdName);
                        sObjectExtIdValue = oldValue.toString();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                        sObjectExtIdValue = getParameter(SOBJECT_EXT_ID_VALUE, exchange, USE_BODY, NOT_OPTIONAL);
                    }

                    final Object finalOldValue = oldValue;
                    restClient.deleteSObjectWithId(sObjectName, sObjectExtIdName, sObjectExtIdValue,
                            new RestClient.ResponseCallback() {
                                @Override
                                public void onResponse(InputStream response, SalesforceException exception) {
                                    processResponse(exchange, response, exception, callback);
                                    restoreFields(exchange, sObjectBase, null, sObjectExtIdName, finalOldValue);
                                }
                            });

                    break;
                }

                case GET_BLOB_FIELD: {
                    // get blob field name
                    final String sObjectBlobFieldName = getParameter(SOBJECT_BLOB_FIELD_NAME,
                            exchange, IGNORE_BODY, NOT_OPTIONAL);

                    // determine parameters from input AbstractSObject
                    final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
                    String sObjectIdValue;
                    if (sObjectBase != null) {
                        sObjectName = sObjectBase.getClass().getSimpleName();
                        sObjectIdValue = sObjectBase.getId();
                    } else {
                        sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
                        sObjectIdValue = getParameter(SOBJECT_ID, exchange, USE_BODY, NOT_OPTIONAL);
                    }
                    final String sObjectId = sObjectIdValue;

                    restClient.getBlobField(sObjectName, sObjectId, sObjectBlobFieldName,
                            new RestClient.ResponseCallback() {
                                @Override
                                public void onResponse(InputStream response, SalesforceException exception) {
                                    processResponse(exchange, response, exception, callback);
                                    restoreFields(exchange, sObjectBase, sObjectId, null, null);
                                }
                            });
                    break;
                }

                case QUERY:
                    final String sObjectQuery = getParameter(SOBJECT_QUERY, exchange, USE_BODY, NOT_OPTIONAL);

                    // use sObject name to load class
                    setResponseClass(exchange, null);

                    restClient.query(sObjectQuery, new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

                case QUERY_MORE:
                    // reuse SOBJECT_QUERY parameter name for nextRecordsUrl
                    final String nextRecordsUrl = getParameter(SOBJECT_QUERY, exchange, USE_BODY, NOT_OPTIONAL);

                    // use custom response class property
                    setResponseClass(exchange, null);

                    restClient.queryMore(nextRecordsUrl, new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

                case SEARCH:
                    final String sObjectSearch = getParameter(SOBJECT_SEARCH, exchange, USE_BODY, NOT_OPTIONAL);

                    restClient.search(sObjectSearch, new RestClient.ResponseCallback() {
                        @Override
                        public void onResponse(InputStream response, SalesforceException exception) {
                            processResponse(exchange, response, exception, callback);
                        }
                    });
                    break;

            }

        } catch (SalesforceException e) {
            exchange.setException(new SalesforceException(
                    String.format("Error processing %s: [%s] \"%s\"",
                            operationName, e.getStatusCode(), e.getMessage()),
                    e));
            callback.done(true);
            return true;
        } catch (RuntimeException e) {
            exchange.setException(new SalesforceException(
                    String.format("Unexpected Error processing %s: \"%s\"",
                            operationName, e.getMessage()),
                    e));
            callback.done(true);
            return true;
        }

        // continue routing asynchronously
        return false;
    }

    private void restoreFields(Exchange exchange, AbstractSObjectBase sObjectBase,
                               String sObjectId, String sObjectExtIdName, Object oldValue) {
        // restore fields
        if (sObjectBase != null) {
            // restore the Id if it was cleared
            if (sObjectId != null) {
                sObjectBase.setId(sObjectId);
            }
            // restore the external id if it was cleared
            if (sObjectExtIdName != null && oldValue != null) {
                try {
                    setPropertyValue(sObjectBase, sObjectExtIdName, oldValue);
                } catch (SalesforceException e) {
                    // YES, the exchange may fail if the property cannot be reset!!!
                    exchange.setException(e);
                }
            }
        }
    }

    private void setPropertyValue(AbstractSObjectBase sObjectBase, String name, Object value) throws SalesforceException {
        try {
            // set the value with the set method
            Method setMethod = sObjectBase.getClass().getMethod("set" + name, value.getClass());
            setMethod.invoke(sObjectBase, value);
        } catch (NoSuchMethodException e) {
            throw new SalesforceException(
                    String.format("SObject %s does not have a field %s",
                            sObjectBase.getClass().getName(), name),
                    e);
        } catch (InvocationTargetException e) {
            throw new SalesforceException(
                    String.format("Error setting value %s.%s",
                            sObjectBase.getClass().getSimpleName(), name),
                    e);
        } catch (IllegalAccessException e) {
            throw new SalesforceException(
                    String.format("Error accessing value %s.%s",
                            sObjectBase.getClass().getSimpleName(), name),
                    e);
        }
    }

    private Object getAndClearPropertyValue(AbstractSObjectBase sObjectBase, String propertyName) throws SalesforceException {
        try {
            // obtain the value using the get method
            Method getMethod = sObjectBase.getClass().getMethod("get" + propertyName);
            Object value = getMethod.invoke(sObjectBase);

            // clear the value with the set method
            Method setMethod = sObjectBase.getClass().getMethod("set" + propertyName, getMethod.getReturnType());
            setMethod.invoke(sObjectBase, new Object[]{null});

            return value;
        } catch (NoSuchMethodException e) {
            throw new SalesforceException(
                    String.format("SObject %s does not have a field %s",
                            sObjectBase.getClass().getSimpleName(), propertyName),
                    e);
        } catch (InvocationTargetException e) {
            throw new SalesforceException(
                    String.format("Error getting/setting value %s.%s",
                            sObjectBase.getClass().getSimpleName(), propertyName),
                    e);
        } catch (IllegalAccessException e) {
            throw new SalesforceException(
                    String.format("Error accessing value %s.%s",
                            sObjectBase.getClass().getSimpleName(), propertyName),
                    e);
        }
    }

    // pre-process request message
    protected abstract void processRequest(Exchange exchange) throws SalesforceException;

    // get request stream from In message
    protected abstract InputStream getRequestStream(Exchange exchange) throws SalesforceException;

    private void setResponseClass(Exchange exchange, String sObjectName) throws SalesforceException {
        Class<?> sObjectClass;

        if (sObjectName != null) {
            // lookup class from class map
            sObjectClass = classMap.get(sObjectName);
            if (null == sObjectClass) {
                throw new SalesforceException(String.format("No class found for SObject %s", sObjectName), null);
            }

        } else {

            // use custom response class property
            final String className = getParameter(SOBJECT_CLASS, exchange, IGNORE_BODY, NOT_OPTIONAL);
            try {
                sObjectClass = endpoint.getComponent().getCamelContext()
                        .getClassResolver().resolveMandatoryClass(className);
            } catch (ClassNotFoundException e) {
                throw new SalesforceException(
                        String.format("SObject class not found %s, %s",
                                className, e.getMessage()),
                        e);
            }
        }
        exchange.setProperty(RESPONSE_CLASS, sObjectClass);
    }

    // process response entity and set out message in exchange
    protected abstract void processResponse(Exchange exchange, InputStream responseEntity, SalesforceException ex, AsyncCallback callback);

}
