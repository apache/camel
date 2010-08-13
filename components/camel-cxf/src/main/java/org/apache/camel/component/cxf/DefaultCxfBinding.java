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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.security.auth.Subject;
import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.Holder;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.attachment.AttachmentImpl;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.MethodDispatcher;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.helpers.XMLUtils;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.security.SecurityContext;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;

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
     * This method is called by {@link CxfProducer#process(Exchange)}. It populates
     * the CXF exchange and invocation context (i.e. request/response) contexts, it 
     * but does not create and populate a CXF message as a ClientImpl's invoke method
     * will create a new CXF Message.  That method will put all properties from the 
     * CXF exchange and request context to the CXF message.
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
        
        // propagate attachments
        Set<Attachment> attachments = null;
        boolean isXop = Boolean.valueOf(camelExchange.getProperty(Message.MTOM_ENABLED, String.class));
        for (Map.Entry<String, DataHandler> entry : camelExchange.getIn().getAttachments().entrySet()) {
            if (attachments == null) {
                attachments = new HashSet<Attachment>();
            }
            AttachmentImpl attachment = new AttachmentImpl(entry.getKey(), entry.getValue());
            attachment.setXOP(isXop);
            attachments.add(attachment);
        }
        
        if (attachments != null) {
            requestContext.put(CxfConstants.CAMEL_CXF_ATTACHMENTS, attachments);
        }
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
            if (!headerFilterStrategy.applyFilterToExternalHeaders(Client.RESPONSE_CONTEXT, 
                                                                   responseContext, camelExchange)) {        
                camelExchange.getOut().setHeader(Client.RESPONSE_CONTEXT, responseContext);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Set header = " + Client.RESPONSE_CONTEXT + " value = " 
                              + responseContext);
                }
            }
        }
        
        // propagate protocol headers
        propagateHeadersFromCxfToCamel(cxfMessage, camelExchange.getOut(), camelExchange);
        
        if (cxfMessage.getAttachments() != null) {
            // TODO: workaround for CXF-2503
            try {
                cxfMessage.getAttachments().size();
            } catch (java.util.ConcurrentModificationException e) {
                // ignore
            }
            // end of workaround

            // propagate attachments
            for (Attachment attachment : cxfMessage.getAttachments()) {
                camelExchange.getOut().addAttachment(attachment.getId(), attachment.getDataHandler());
            }        
        }
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
        
        // propagate the security subject from CXF security context
        SecurityContext securityContext = cxfMessage.get(SecurityContext.class);
        if (securityContext != null && securityContext.getUserPrincipal() != null) {
            Subject subject = new Subject();
            subject.getPrincipals().add(securityContext.getUserPrincipal());
            camelExchange.getIn().getHeaders().put(Exchange.AUTHENTICATION, subject);
        }
        
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
        
        // propagate attachments
        if (cxfMessage.getAttachments() != null) {
            for (Attachment attachment : cxfMessage.getAttachments()) {
                camelExchange.getIn().addAttachment(attachment.getId(), attachment.getDataHandler());
            }
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
        
        // make sure the "requestor role" property does not get propagated as we do switch role
        responseContext.remove(Message.REQUESTOR_ROLE);
        
        outMessage.putAll(responseContext);
        
        // Do we still need to put the response context back like this
        outMessage.put(Client.RESPONSE_CONTEXT, responseContext);      
        
        if (LOG.isTraceEnabled()) {
            LOG.trace("Set out response context = " + responseContext);
        }
        
        // set body
        Object outBody = DefaultCxfBinding.getBodyFromCamel(camelExchange.getOut(), dataFormat);
        
        if (outBody != null) {
            if (dataFormat == DataFormat.PAYLOAD) {
                CxfPayload<?> payload = (CxfPayload<?>)outBody;
                outMessage.setContent(List.class, getResponsePayloadList(cxfExchange, payload.getBody()));
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
        
        // propagate attachments
        Set<Attachment> attachments = null;
        boolean isXop = Boolean.valueOf(camelExchange.getProperty(Message.MTOM_ENABLED, String.class));
        for (Map.Entry<String, DataHandler> entry : camelExchange.getOut().getAttachments().entrySet()) {
            if (attachments == null) {
                attachments = new HashSet<Attachment>();
            }
            AttachmentImpl attachment = new AttachmentImpl(entry.getKey(), entry.getValue());
            attachment.setXOP(isXop); 
            attachments.add(attachment);
        }
        
        if (attachments != null) {
            outMessage.setAttachments(attachments);
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
    
    protected MessageContentsList getResponsePayloadList(org.apache.cxf.message.Exchange exchange, 
                                                         List<Element> elements) {
        BindingOperationInfo boi = exchange.getBindingOperationInfo();

        if (boi.isUnwrapped()) {
            boi = boi.getWrappedOperation();
            exchange.put(BindingOperationInfo.class, boi);
        }
        
        MessageContentsList answer = new MessageContentsList();

        int i = 0;
        
        for (MessagePartInfo partInfo : boi.getOperationInfo().getOutput().getMessageParts()) {
            if (elements.size() > i) {
                answer.put(partInfo, elements.get(i++));
                
            }
        }

        return answer;
        
    }
    
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
    @SuppressWarnings("unchecked")
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
        
        
        // propagate SOAP/protocol header list
        String key = Header.HEADER_LIST;
        Object value = cxfMessage.get(key);
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

    @SuppressWarnings("unchecked")
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
                List<SoapHeader> headers = CastUtils.cast((List<?>)message.get(Header.HEADER_LIST));
                answer = new CxfPayload<SoapHeader>(headers, getPayloadBodyElements(message));
                
            } else if (dataFormat == DataFormat.MESSAGE) {
                answer = message.getContent(InputStream.class);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Extracted body from CXF message = " + answer);
            }
        }
        return answer;
    }
    

    protected static List<Element> getPayloadBodyElements(Message message) {
        MessageContentsList inObjects = MessageContentsList.getContentsList(message);
        org.apache.cxf.message.Exchange exchange = message.getExchange();
        BindingOperationInfo boi = exchange.getBindingOperationInfo();

        OperationInfo op = boi.getOperationInfo();
        
        if (boi.isUnwrapped()) {
            op = boi.getWrappedOperation().getOperationInfo();
        } 

        List<MessagePartInfo> partInfos = null;
        boolean client = Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
        if (client) {
            // it is a response
            partInfos = op.getOutput().getMessageParts();            
            
        } else {
            // it is a request
            partInfos = op.getInput().getMessageParts();            
        }
        
        List<Element> answer = new ArrayList<Element>();

        for (MessagePartInfo partInfo : partInfos) {
            if (!inObjects.hasValue(partInfo)) {
                continue;
            }
            
            Object part = inObjects.get(partInfo);
            
            if (part instanceof Holder) {
                part = ((Holder)part).value;
            }
                        
            if (part instanceof DOMSource) {
                Element element = getFirstElement(((DOMSource)part).getNode());

                if (element != null) {
                    answer.add(element);
                }
                
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extract body element " 
                              + (element == null ? "null" : XMLUtils.toString(element)));
                }
                
            } else if (part instanceof Element) {
                answer.add((Element)part);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unhandled part type '" + part.getClass());
                }
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
            answer = out.getBody(CxfPayload.class);
        } else if (dataFormat == DataFormat.MESSAGE) {
            answer = out.getBody(InputStream.class);
        }
        return answer;
    }

    private static Element getFirstElement(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            return (Element)node;
        } 
        
        return DOMUtils.getFirstElement(node);
    }
    
    public void copyJaxWsContext(org.apache.cxf.message.Exchange cxfExchange, Map<String, Object> context) {
        if (cxfExchange.getOutMessage() != null) {
            org.apache.cxf.message.Message outMessage = cxfExchange.getOutMessage();
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (outMessage.get(entry.getKey()) == null) {
                    outMessage.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public void extractJaxWsContext(org.apache.cxf.message.Exchange cxfExchange, Map<String, Object> context) {
        org.apache.cxf.message.Message inMessage = cxfExchange.getInMessage();
        for (Map.Entry<String, Object> entry : inMessage.entrySet()) {
            if (entry.getKey().startsWith("javax.xml.ws")) {
                context.put(entry.getKey(), entry.getValue());
            }
        }
        
    }

}
