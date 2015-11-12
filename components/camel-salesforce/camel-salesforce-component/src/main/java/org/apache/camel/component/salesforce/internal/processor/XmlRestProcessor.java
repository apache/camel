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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;
import com.thoughtworks.xstream.mapper.CachingMapper;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.api.JodaTimeConverter;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.RestResources;
import org.apache.camel.component.salesforce.api.dto.SObjectBasicInfo;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SearchResults;
import org.apache.camel.component.salesforce.api.dto.Versions;
import org.apache.camel.component.salesforce.internal.client.XStreamUtils;
import org.eclipse.jetty.util.StringUtil;

import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_NAME;

public class XmlRestProcessor extends AbstractRestProcessor {

    // although XStream is generally thread safe, because of the way we use aliases
    // for GET_BASIC_INFO and GET_DESCRIPTION, we need to use a ThreadLocal
    // not very efficient when both JSON and XML are used together with a single Thread pool
    // but this will do for now
    private static ThreadLocal<XStream> xStream =
        new ThreadLocal<XStream>() {
            @Override
            protected XStream initialValue() {
                // use NoNameCoder to avoid escaping __ in custom field names
                // and CompactWriter to avoid pretty printing
                XStream result = new XStream(new XppDriver(new NoNameCoder()) {
                    @Override
                    public HierarchicalStreamWriter createWriter(Writer out) {
                        return new CompactWriter(out, getNameCoder());
                    }

                });
                XStreamUtils.addDefaultPermissions(result);
                result.registerConverter(new JodaTimeConverter());
                return result;
            }
        };

    private static final String RESPONSE_ALIAS = XmlRestProcessor.class.getName() + ".responseAlias";

    public XmlRestProcessor(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

    }

    @Override
    protected void processRequest(Exchange exchange) throws SalesforceException {

        switch (operationName) {
        case GET_VERSIONS:
            exchange.setProperty(RESPONSE_CLASS, Versions.class);
            break;

        case GET_RESOURCES:
            exchange.setProperty(RESPONSE_CLASS, RestResources.class);
            break;

        case GET_GLOBAL_OBJECTS:
            // handle in built response types
            exchange.setProperty(RESPONSE_CLASS, GlobalObjects.class);
            break;

        case GET_BASIC_INFO:
            // handle in built response types
            exchange.setProperty(RESPONSE_CLASS, SObjectBasicInfo.class);

            // need to add alias for Salesforce XML that uses SObject name as root element
            exchange.setProperty(RESPONSE_ALIAS, getParameter(SOBJECT_NAME, exchange, USE_BODY, NOT_OPTIONAL));
            break;

        case GET_DESCRIPTION:
            // handle in built response types
            exchange.setProperty(RESPONSE_CLASS, SObjectDescription.class);

            // need to add alias for Salesforce XML that uses SObject name as root element
            exchange.setProperty(RESPONSE_ALIAS, getParameter(SOBJECT_NAME, exchange, USE_BODY, NOT_OPTIONAL));
            break;

        case GET_SOBJECT:
            // need to add alias for Salesforce XML that uses SObject name as root element
            exchange.setProperty(RESPONSE_ALIAS, getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL));
            break;

        case CREATE_SOBJECT:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, CreateSObjectResult.class);
            break;

        case GET_SOBJECT_WITH_ID:
            // need to add alias for Salesforce XML that uses SObject name as root element
            exchange.setProperty(RESPONSE_ALIAS, getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL));
            break;

        case UPSERT_SOBJECT:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, CreateSObjectResult.class);
            break;

        case QUERY:
        case QUERY_MORE:
            // need to add alias for Salesforce XML that uses SObject name as root element
            exchange.setProperty(RESPONSE_ALIAS, "QueryResult");
            break;

        case SEARCH:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, SearchResults.class);
            break;

        case APEX_CALL:
            // need to add alias for Salesforce XML that uses SObject name as root element
            exchange.setProperty(RESPONSE_ALIAS, "response");
            break;

        default:
            // ignore, some operations do not require alias or class exchange properties
        }
    }

    protected InputStream getRequestStream(Exchange exchange) throws SalesforceException {
        final XStream localXStream = xStream.get();
        try {
            // get request stream from In message
            Message in = exchange.getIn();
            InputStream request = in.getBody(InputStream.class);
            if (request == null) {
                AbstractDTOBase dto = in.getBody(AbstractDTOBase.class);
                if (dto != null) {
                    // marshall the DTO
                    // first process annotations on the class, for things like alias, etc.
                    localXStream.processAnnotations(dto.getClass());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    // make sure we write the XML with the right encoding
                    localXStream.toXML(dto, new OutputStreamWriter(out, StringUtil.__UTF8_CHARSET));
                    request = new ByteArrayInputStream(out.toByteArray());
                } else {
                    // if all else fails, get body as String
                    final String body = in.getBody(String.class);
                    if (null == body) {
                        String msg = "Unsupported request message body "
                            + (in.getBody() == null ? null : in.getBody().getClass());
                        throw new SalesforceException(msg, null);
                    } else {
                        request = new ByteArrayInputStream(body.getBytes(StringUtil.__UTF8_CHARSET));
                    }
                }
            }
            return request;
        } catch (XStreamException e) {
            String msg = "Error marshaling request: " + e.getMessage();
            throw new SalesforceException(msg, e);
        }
    }

    @Override
    protected void processResponse(Exchange exchange, InputStream responseEntity,
                                   SalesforceException exception, AsyncCallback callback) {
        final XStream localXStream = xStream.get();
        try {
            // do we need to un-marshal a response
            if (responseEntity != null) {
                final Class<?> responseClass = exchange.getProperty(RESPONSE_CLASS, Class.class);
                Object response;
                if (responseClass != null) {
                    // its ok to call this multiple times, as xstream ignores duplicate calls
                    localXStream.processAnnotations(responseClass);
                    final String responseAlias = exchange.getProperty(RESPONSE_ALIAS, String.class);
                    if (responseAlias != null) {
                        // extremely dirty, need to flush entire cache if its holding on to an old alias!!!
                        final CachingMapper mapper = (CachingMapper) localXStream.getMapper();
                        try {
                            if (mapper.realClass(responseAlias) != responseClass) {
                                mapper.flushCache();
                            }
                        } catch (CannotResolveClassException ignore) {
                            // recent XStream versions add a ClassNotFoundException to cache
                            mapper.flushCache();
                        }
                        localXStream.alias(responseAlias, responseClass);
                    }
                    response = responseClass.newInstance();
                    localXStream.fromXML(responseEntity, response);
                } else {
                    // return the response as a stream, for getBlobField
                    response = responseEntity;
                }
                exchange.getOut().setBody(response);
            } else {
                exchange.setException(exception);
            }
            // copy headers and attachments
            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
            exchange.getOut().getAttachments().putAll(exchange.getIn().getAttachments());
        } catch (XStreamException e) {
            String msg = "Error parsing XML response: " + e.getMessage();
            exchange.setException(new SalesforceException(msg, e));
        } catch (Exception e) {
            String msg = "Error creating XML response: " + e.getMessage();
            exchange.setException(new SalesforceException(msg, e));
        } finally {
            // cleanup temporary exchange headers
            exchange.removeProperty(RESPONSE_CLASS);
            exchange.removeProperty(RESPONSE_ALIAS);

            // consume response entity
            if (responseEntity != null) {
                try {
                    responseEntity.close();
                } catch (IOException ignored) {
                }
            }

            // notify callback that exchange is done
            callback.done(false);
        }
    }

}
