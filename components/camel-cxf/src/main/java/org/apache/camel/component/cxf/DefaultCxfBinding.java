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
package org.apache.camel.component.cxf;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.HeaderFilterStrategyAware;
import org.apache.camel.component.cxf.transport.CamelTransportConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.MethodDispatcher;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;

/**
 * The Default CXF binding implementation.
 * 
 * @version $Revision$
 */
public class DefaultCxfBinding implements CxfBinding, HeaderFilterStrategyAware {
    private static final Log LOG = LogFactory.getLog(DefaultCxfBinding.class);
    private HeaderFilterStrategy headerFilterStrategy;

    // CxfBinding Methods
    // -------------------------------------------------------------------------
    
    /**
     * <p>
     * This method is called by {@link CxfProducer#process(Exchange)}. It populates 
     * the CXF exchange and invocation context (i.e. request/response) contexts, it 
     * but does not create and populate a CXF message as a ClientImpl's invoke method
     * will create a new CXF Message.  That method will put all properties from the 
     * CXF exchange and request context to the CXF message.
     * </p>
     */
    public void populateCxfRequestFromExchange(
            org.apache.cxf.message.Exchange cxfExchange, Exchange camelExchange,
            Map<String, Object> requestContext) {
               
        // propagate request context
        Map<String, Object> camelHeaders = camelExchange.getIn().getHeaders();
        extractInvocationContextFromCamel(camelExchange, camelHeaders,
                requestContext, Client.REQUEST_CONTEXT);
                
        // propagate headers
        propagateHeadersFromCamelToCxf(camelExchange, camelHeaders, cxfExchange, 
                requestContext);
        
    }
    
    /**
     * This method is called by {@link CxfProducer#process(Exchange)}.  It propagates 
     * information from CXF Exchange to Camel Exchange.  The CXF Exchange contains a 
     * request from a CXF server.
     */
    public void populateExchangeFromCxfResponse(Exchange camelExchange,
            org.apache.cxf.message.Exchange cxfExchange, 
            Map<String, Object> responseContext) {
      
        Message cxfMessage = cxfExchange.getInMessage();
        
        if (LOG.isTraceEnabled()) {
            LOG.trace("Populate exchange from CXF response message: " + cxfMessage);
        }
        
        // propagate body
        camelExchange.getOut().setBody(DefaultCxfBinding.getContentFromCxf(cxfMessage, 
                camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class)));
        
        // propagate response context
        if (responseContext != null && responseContext.size() > 0) {
            camelExchange.getOut().setHeader(Client.RESPONSE_CONTEXT, responseContext);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Set header = " + Client.RESPONSE_CONTEXT + " value = " 
                        + responseContext);
            }
        }
        
        // propagate protocol headers
        propagateHeadersFromCxfToCamel(cxfMessage, camelExchange.getOut(), camelExchange);
    }
    
    /**
     * This method is called by {@link CxfConsumer}.
     */
    public void populateExchangeFromCxfRequest(org.apache.cxf.message.Exchange cxfExchange,
            Exchange camelExchange) {
        
        Method method = null;
        QName operationName = null;
        ExchangePattern mep = ExchangePattern.InOut;
        
        // extract binding operation information
        BindingOperationInfo boi = camelExchange.getProperty(BindingOperationInfo.class.getName(), 
                                                             BindingOperationInfo.class);
        if (boi != null) {
            Service service = (Service)cxfExchange.get(Service.class); 
            if (service != null) {
                MethodDispatcher md = (MethodDispatcher)service
                    .get(MethodDispatcher.class.getName());
                if (md != null) {
                    method = md.getMethod(boi);
                }
            }
            
            if (boi.getOperationInfo().isOneWay()) {
                mep = ExchangePattern.InOnly;
            }
                
            operationName = boi.getName();
        }
        
        // set operation name in header
        if (operationName != null) {
            camelExchange.getIn().setHeader(CxfConstants.OPERATION_NAMESPACE, 
                    boi.getName().getNamespaceURI());
            camelExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, 
                    boi.getName().getLocalPart());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Set IN header: " + CxfConstants.OPERATION_NAMESPACE + "=" 
                         + boi.getName().getNamespaceURI());
                LOG.trace("Set IN header: " + CxfConstants.OPERATION_NAME + "=" 
                        + boi.getName().getLocalPart());
            }
        } else if (method != null) {
            camelExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, method.getName());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Set IN header: " + CxfConstants.OPERATION_NAME + "=" 
                        + method.getName());
            }
        }
                
        // set message exchange pattern
        camelExchange.setPattern(mep);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Set exchange MEP: " + mep);
        }

        // propagate headers
        Message cxfMessage = cxfExchange.getInMessage();
        propagateHeadersFromCxfToCamel(cxfMessage, camelExchange.getIn(), camelExchange);
        
        // Propagating properties from CXF Exchange to Camel Exchange has an  
        // side effect of copying reply side stuff when the producer is retried.
        // So, we do not want to do this.
        //camelExchange.getProperties().putAll(cxfExchange);
        
        // propagate request context
        Object value = cxfMessage.get(Client.REQUEST_CONTEXT);
        if (value != null && !headerFilterStrategy.applyFilterToExternalHeaders(
                Client.REQUEST_CONTEXT, value, camelExchange)) {
            camelExchange.getIn().setHeader(Client.REQUEST_CONTEXT, value);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Populate context from CXF message " + Client.REQUEST_CONTEXT 
                        + " value=" + value);
            }
        }
           
        // set body
        Object body = DefaultCxfBinding.getContentFromCxf(cxfMessage, 
                camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class));
        if (body != null) {
            camelExchange.getIn().setBody(body);
        }  
    }

    /**
     * This method is called by {@link CxfConsumer} to populate a CXF response exchange 
     * from a Camel exchange.
     */
    public void populateCxfResponseFromExchange(Exchange camelExchange, 
            org.apache.cxf.message.Exchange cxfExchange) {
        
        // create response context
        Map<String, Object> responseContext = new HashMap<String, Object>();
        
        // propagate response context
        Map<String, Object> camelHeaders = camelExchange.getOut().getHeaders();
        extractInvocationContextFromCamel(camelExchange, camelHeaders, 
                responseContext, Client.RESPONSE_CONTEXT);
      
        propagateHeadersFromCamelToCxf(camelExchange, camelHeaders, cxfExchange, 
                responseContext);
        // create out message
        Endpoint ep = cxfExchange.get(Endpoint.class);
        Message outMessage = ep.getBinding().createMessage();
        cxfExchange.setOutMessage(outMessage);       

        DataFormat dataFormat = camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY,  
                DataFormat.class);
        
        // propagate contexts
        if (dataFormat != DataFormat.POJO) {
            // copying response context to out message seems to cause problem in POJO mode
            outMessage.putAll(responseContext);
        }
        outMessage.put(Client.RESPONSE_CONTEXT, responseContext);      
        
        if (LOG.isTraceEnabled()) {
            LOG.trace("Set out response context = " + responseContext);
        }
        
        // set body
        Object outBody = DefaultCxfBinding.getBodyFromCamel(camelExchange.getOut(), dataFormat);
        
        if (outBody != null) {
            if (dataFormat == DataFormat.PAYLOAD) {
                CxfPayload<?> payload = (CxfPayload<?>)outBody;
                outMessage.put(List.class, payload.getBody());
                outMessage.put(Header.HEADER_LIST, payload.getHeaders());
            } else {
                if (responseContext.get(Header.HEADER_LIST) != null) {
                    outMessage.put(Header.HEADER_LIST, responseContext.get(Header.HEADER_LIST));
                }

                MessageContentsList resList = null;
                if (outBody instanceof MessageContentsList) {
                    resList = (MessageContentsList)outBody;
                } else if (outBody instanceof List) {
                    resList = new MessageContentsList((List<?>)outBody);
                } else if (outBody.getClass().isArray()) {
                    resList = new MessageContentsList((Object[])outBody);
                } else {
                    resList = new MessageContentsList(outBody);
                }

                if (resList != null) {
                    outMessage.setContent(List.class, resList);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Set Out CXF message content = " + resList);
                    }
                }
            }
        }         
       
        BindingOperationInfo boi = cxfExchange.get(BindingOperationInfo.class);
        if (boi != null) {
            cxfExchange.put(BindingMessageInfo.class, boi.getOutput());
        }
        
    }

    
    // HeaderFilterStrategyAware Methods
    // -------------------------------------------------------------------------
    

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

       
    // Non public methods
    // -------------------------------------------------------------------------
    
    /**
     * @param camelExchange
     * @param cxfContext Request or Response context
     * @param camelHeaders 
     * @param contextKey 
     */
    @SuppressWarnings("unchecked")
    protected void extractInvocationContextFromCamel(Exchange camelExchange,
            Map<String, Object> camelHeaders, Map<String, Object> cxfContext,
            String contextKey) {
        
        // extract from header
        Map context = (Map)camelHeaders.get(contextKey);
        if (context != null) {
            cxfContext.putAll(context);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Propagate " + contextKey + " from header context = " 
                        + ((context instanceof WrappedMessageContext) 
                                ? ((WrappedMessageContext)context).getWrappedMap() 
                                        : context));
            }
        }
        
        // extract from exchange property
        context = (Map)camelExchange.getProperty(contextKey);
        if (context != null) {
            cxfContext.putAll(context);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Propagate " + contextKey + " from exchange property context = " 
                        + ((context instanceof WrappedMessageContext) 
                                ? ((WrappedMessageContext)context).getWrappedMap() 
                                        : context));
            }
        }
        
        // copy camel exchange properties into context
        if (camelExchange.getProperties() != null) {
            cxfContext.putAll(camelExchange.getProperties());
        }
        
        camelExchange.setProperty(contextKey, cxfContext);
    }

    /**
     * @param cxfMessage
     * @param camelMessage
     * @param exchange provides context for filtering
     */
    protected void propagateHeadersFromCxfToCamel(Message cxfMessage,
            org.apache.camel.Message camelMessage, Exchange exchange) {
        
        Map<String, List<String>> cxfHeaders = (Map)cxfMessage.get(Message.PROTOCOL_HEADERS);
        Map<String, Object> camelHeaders = camelMessage.getHeaders();

        if (cxfHeaders != null) {
            for (Map.Entry<String, List<String>> entry : cxfHeaders.entrySet()) {
                if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), 
                                                                       entry.getValue(), exchange)) {
                    camelHeaders.put(entry.getKey(), entry.getValue().get(0));
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Populate header from CXF header=" + entry.getKey() + " value="
                                + entry.getValue());
                    }
                }
            }
        }

        // propagate content type
        String key = Message.CONTENT_TYPE;
        Object value = cxfMessage.get(key);
        if (value != null && !headerFilterStrategy.applyFilterToExternalHeaders(key, value, exchange)) {
            camelHeaders.put(CamelTransportConstants.CONTENT_TYPE, value);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Populate header from CXF header=" + key + " value=" + value);
            }
        }
        
        // propagate SOAP/protocol header list
        key = Header.HEADER_LIST;
        value = cxfMessage.get(key);
        if (value != null) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(key, value, exchange)) {
                camelHeaders.put(key, value);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Populate header from CXF header=" + key + " value=" + value);
                }
            } else {
                ((List<?>)value).clear();
            }
        }
              
    }

    protected void propagateHeadersFromCamelToCxf(Exchange camelExchange, 
            Map<String, Object> camelHeaders,
            org.apache.cxf.message.Exchange cxfExchange, 
            Map<String, Object> cxfContext) {
        
        // get cxf transport headers (if any) from camel exchange
        Map<String, List<String>> transportHeaders = new HashMap<String, List<String>>();
        if (camelExchange != null) {
            Map<String, List<String>> h = (Map)camelExchange.getProperty(Message.PROTOCOL_HEADERS);
            if (h != null) {
                transportHeaders.putAll(h);
            }
        }
        Map<String, List<String>> headers = (Map)camelHeaders.get(Message.PROTOCOL_HEADERS);
        if (headers != null) {
            transportHeaders.putAll(headers);
        }
            
        for (Map.Entry<String, Object> entry : camelHeaders.entrySet()) {    
            // this header should be filtered, continue to the next header
            if (headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), camelExchange)) {
                continue;
            }
            
            if (LOG.isTraceEnabled()) {
                LOG.trace("Propagate to CXF header: " + entry.getKey() + " value: " + entry.getValue());
            }
            
            // put content type in exchange
            if (CamelTransportConstants.CONTENT_TYPE.equals(entry.getKey())) {
                cxfExchange.put(Message.CONTENT_TYPE, entry.getValue());
                continue;
            }
            
            // put response code in request context so it will be copied to CXF message's property
            if (Message.RESPONSE_CODE.equals(entry.getKey())) {
                cxfContext.put(entry.getKey(), entry.getValue());
                continue;
            }
            
            // put SOAP/protocol header list in exchange
            if (Header.HEADER_LIST.equals(entry.getKey())) {
                List<Header> headerList = (List<Header>)entry.getValue();
                for (Header header : headerList) {
                    header.setDirection(Header.Direction.DIRECTION_OUT);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Propagate SOAP/protocol header: " + header.getName() + " : " + header.getObject());
                    }
                }
                
                //cxfExchange.put(Header.HEADER_LIST, headerList);
                cxfContext.put(entry.getKey(), headerList);
                continue;
            }
            
            // things that are not filtered and not specifically copied will be put in transport headers
            if (entry.getValue() instanceof List) {
                transportHeaders.put(entry.getKey(), (List<String>)entry.getValue());
            } else {
                List<String> listValue = new ArrayList<String>();
                listValue.add(entry.getValue().toString());
                transportHeaders.put(entry.getKey(), listValue);
            }
        }
        
        if (transportHeaders.size() > 0) {
            cxfContext.put(Message.PROTOCOL_HEADERS, transportHeaders);
        }        
    }

    protected static Object getContentFromCxf(Message message, DataFormat dataFormat) {
        Set<Class<?>> contentFormats = message.getContentFormats();
        Object answer = null;
        if (contentFormats != null) {
            
            if (LOG.isTraceEnabled()) {
                for (Class<?> contentFormat : contentFormats) {
                    LOG.trace("Content format=" + contentFormat + " value=" 
                            + message.getContent(contentFormat));
                }
            }
            
            if (dataFormat == DataFormat.POJO) {
                answer = message.getContent(List.class);  
                if (answer == null) {
                    answer = message.getContent(Object.class);
                    if (answer != null) {
                        answer = new MessageContentsList(answer);
                    }
                }
            } else if (dataFormat == DataFormat.PAYLOAD) {
                // TODO handle other message types in the future.  Currently, this binding only 
                // deal with SOAP in PayLoad mode.
                List<Element> body = message.get(List.class);
                List<SoapHeader> headers = CastUtils.cast((List<?>)message.get(Header.HEADER_LIST));
                answer = new CxfPayload<SoapHeader>(headers, body);
            } else if (dataFormat == DataFormat.MESSAGE) {
                answer = message.getContent(InputStream.class);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Extracted body from CXF message = " + answer);
            }
        }
        return answer;
    }
    
    public static Object getBodyFromCamel(org.apache.camel.Message out,
            DataFormat dataFormat) {
        Object answer = null;
        
        if (dataFormat == DataFormat.POJO) {
            answer = out.getBody();
        } else if (dataFormat == DataFormat.PAYLOAD) {
            answer = out.getBody();
        } else if (dataFormat == DataFormat.MESSAGE) {
            answer = out.getBody(InputStream.class);
        }
        return answer;
    }

}
