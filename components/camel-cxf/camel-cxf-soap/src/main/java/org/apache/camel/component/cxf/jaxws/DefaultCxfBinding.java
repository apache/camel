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
package org.apache.camel.component.cxf.jaxws;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

import jakarta.activation.DataHandler;
import jakarta.xml.ws.Holder;

import javax.security.auth.Subject;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.component.cxf.common.CxfBinding;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.common.header.CxfHeaderHelper;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.util.ReaderInputStream;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.cxf.attachment.AttachmentImpl;
import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.Soap12;
import org.apache.cxf.binding.soap.SoapBindingConstants;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.security.LoginSecurityContext;
import org.apache.cxf.security.SecurityContext;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.invoker.MethodDispatcher;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.staxutils.StaxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Default CXF binding implementation.
 */
public class DefaultCxfBinding implements CxfBinding, HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCxfBinding.class);
    private HeaderFilterStrategy headerFilterStrategy;

    // CxfBinding Methods
    // -------------------------------------------------------------------------

    /**
     * This method is called by {@link CxfProducer#process(Exchange)}. It populates the CXF exchange and invocation
     * context (i.e. request/response) contexts, it but does not create and populate a CXF message as a ClientImpl's
     * invoke method will create a new CXF Message. That method will put all properties from the CXF exchange and
     * request context to the CXF message.
     */
    @Override
    public void populateCxfRequestFromExchange(
            org.apache.cxf.message.Exchange cxfExchange, Exchange camelExchange,
            Map<String, Object> requestContext) {

        // propagate request context
        Map<String, Object> camelHeaders = camelExchange.getIn().getHeaders();
        extractInvocationContextFromCamel(camelExchange, camelHeaders,
                requestContext, CxfConstants.REQUEST_CONTEXT);

        // propagate headers
        propagateHeadersFromCamelToCxf(camelExchange, camelHeaders, cxfExchange,
                requestContext);

        String overrideAddress = camelExchange.getIn().getHeader(CxfConstants.DESTINATION_OVERRIDE_URL, String.class);

        if (overrideAddress != null) {
            LOG.trace("Client address is overridden by header '{}' to value '{}'",
                    CxfConstants.DESTINATION_OVERRIDE_URL, overrideAddress);
            requestContext.put(Message.ENDPOINT_ADDRESS, overrideAddress);
        }

        // propagate attachments
        Set<Attachment> attachments = null;
        boolean isXop = Boolean.valueOf(camelExchange.getProperty(Message.MTOM_ENABLED, String.class));
        DataFormat dataFormat = camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY,
                DataFormat.class);
        // we should avoid adding the attachments if the data format is CXFMESSAGE, as the message stream
        // already has the attachment information
        if (!DataFormat.CXF_MESSAGE.equals(dataFormat)) {
            if (camelExchange.getIn(AttachmentMessage.class).hasAttachments()) {
                for (Map.Entry<String, org.apache.camel.attachment.Attachment> entry : camelExchange
                        .getIn(AttachmentMessage.class).getAttachmentObjects().entrySet()) {
                    if (attachments == null) {
                        attachments = new HashSet<>();
                    }
                    AttachmentImpl attachment = new AttachmentImpl(entry.getKey());
                    org.apache.camel.attachment.Attachment camelAttachment = entry.getValue();
                    attachment.setDataHandler(camelAttachment.getDataHandler());
                    for (String name : camelAttachment.getHeaderNames()) {
                        attachment.setHeader(name, camelAttachment.getHeader(name));
                    }
                    attachment.setXOP(isXop);
                    attachments.add(attachment);
                }
            }
        }

        if (attachments != null) {
            requestContext.put(CxfConstants.CAMEL_CXF_ATTACHMENTS, attachments);
        }
    }

    /**
     * This method is called by {@link CxfProducer#process(Exchange)}. It propagates information from CXF Exchange to
     * Camel Exchange. The CXF Exchange contains a request from a CXF server.
     */
    @Override
    public void populateExchangeFromCxfResponse(
            Exchange camelExchange,
            org.apache.cxf.message.Exchange cxfExchange,
            Map<String, Object> responseContext) {

        Message cxfMessage = cxfExchange.getInMessage();
        // Need to check if the inMessage is set
        if (cxfMessage == null) {
            return;
        }

        LOG.trace("Populate exchange from CXF response message: {}", cxfMessage);

        // copy the InMessage header to OutMessage header
        camelExchange.getMessage().getHeaders().putAll(camelExchange.getIn().getHeaders());

        // propagate body
        String encoding = (String) camelExchange.getProperty(ExchangePropertyKey.CHARSET_NAME);
        camelExchange.getMessage().setBody(DefaultCxfBinding.getContentFromCxf(cxfMessage,
                camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class), encoding));

        // propagate response context
        if (responseContext != null && responseContext.size() > 0) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(CxfConstants.RESPONSE_CONTEXT,
                    responseContext, camelExchange)) {
                camelExchange.getMessage().setHeader(CxfConstants.RESPONSE_CONTEXT, responseContext);
                LOG.trace("Set header = {} value = {}", CxfConstants.RESPONSE_CONTEXT, responseContext);
            }
        }

        // propagate protocol headers
        propagateHeadersFromCxfToCamel(cxfMessage, camelExchange.getMessage(), camelExchange);

        // propagate attachments
        if (cxfMessage.getAttachments() != null) {
            // propagate attachments
            for (Attachment attachment : cxfMessage.getAttachments()) {
                camelExchange.getMessage(AttachmentMessage.class).addAttachmentObject(attachment.getId(),
                        createCamelAttachment(attachment));
            }
        }
        addAttachmentFileCloseUoW(camelExchange, cxfExchange);
    }

    /**
     * CXF may cache attachments in the filesystem temp folder. The files may leak if they were not used. Add a cleanup
     * handler to remove the attachments after message processing.
     *
     * @param camelExchange
     * @param cxfExchange
     */
    private void addAttachmentFileCloseUoW(Exchange camelExchange, org.apache.cxf.message.Exchange cxfExchange) {
        camelExchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onDone(org.apache.camel.Exchange exchange) {
                Collection<Attachment> atts = cxfExchange.getInMessage().getAttachments();
                if (atts != null) {
                    for (Attachment att : atts) {
                        DataHandler dh = att.getDataHandler();
                        if (dh != null) {
                            try {
                                InputStream is = dh.getInputStream();
                                IOHelper.close(is);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        });
    }

    private DefaultAttachment createCamelAttachment(Attachment attachment) {
        DefaultAttachment camelAttachment = new DefaultAttachment(attachment.getDataHandler());
        Iterator<String> headers = attachment.getHeaderNames();
        while (headers.hasNext()) {
            String name = headers.next();
            camelAttachment.addHeader(name, attachment.getHeader(name));
        }
        return camelAttachment;
    }

    /**
     * This method is called by {@link CxfConsumer}.
     */
    @Override
    public void populateExchangeFromCxfRequest(
            org.apache.cxf.message.Exchange cxfExchange,
            Exchange camelExchange) {

        Method method = null;
        QName operationName = null;
        ExchangePattern mep = ExchangePattern.InOut;

        // extract binding operation information
        BindingOperationInfo boi = camelExchange.getProperty(BindingOperationInfo.class.getName(),
                BindingOperationInfo.class);
        if (boi != null) {
            Service service = cxfExchange.get(Service.class);
            if (service != null) {
                MethodDispatcher md = (MethodDispatcher) service.get(MethodDispatcher.class.getName());
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
                LOG.trace("Set IN header: {}={}",
                        CxfConstants.OPERATION_NAMESPACE, boi.getName().getNamespaceURI());
                LOG.trace("Set IN header: {}={}",
                        CxfConstants.OPERATION_NAME, boi.getName().getLocalPart());
            }
        } else if (method != null) {
            camelExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, method.getName());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Set IN header: {}={}",
                        CxfConstants.OPERATION_NAME, method.getName());
            }
        }

        // set message exchange pattern
        camelExchange.setPattern(mep);
        LOG.trace("Set exchange MEP: {}", mep);

        // propagate headers
        Message cxfMessage = cxfExchange.getInMessage();
        propagateHeadersFromCxfToCamel(cxfMessage, camelExchange.getIn(), camelExchange);

        // propagate the security subject from CXF security context
        SecurityContext securityContext = cxfMessage.get(SecurityContext.class);
        if (securityContext instanceof LoginSecurityContext
                && ((LoginSecurityContext) securityContext).getSubject() != null) {
            camelExchange.getIn().getHeaders().put(CxfConstants.AUTHENTICATION,
                    ((LoginSecurityContext) securityContext).getSubject());
        } else if (securityContext != null) {
            Principal user = securityContext.getUserPrincipal();
            if (user != null) {
                Subject subject = new Subject();
                subject.getPrincipals().add(user);
                camelExchange.getIn().getHeaders().put(CxfConstants.AUTHENTICATION, subject);
            }
        }

        // Propagating properties from CXF Exchange to Camel Exchange has an
        // side effect of copying reply side stuff when the producer is retried.
        // So, we do not want to do this.
        //camelExchange.getProperties().putAll(cxfExchange);

        // propagate request context
        Object value = cxfMessage.get(CxfConstants.REQUEST_CONTEXT);
        if (value != null && !headerFilterStrategy.applyFilterToExternalHeaders(
                CxfConstants.REQUEST_CONTEXT, value, camelExchange)) {
            camelExchange.getIn().setHeader(CxfConstants.REQUEST_CONTEXT, value);
            LOG.trace("Populate context from CXF message {} value={}", CxfConstants.REQUEST_CONTEXT, value);
        }

        // setup the charset from content-type header
        setCharsetWithContentType(camelExchange);

        // set body
        String encoding = (String) camelExchange.getProperty(ExchangePropertyKey.CHARSET_NAME);
        Object body = DefaultCxfBinding.getContentFromCxf(cxfMessage,
                camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class), encoding);
        if (body != null) {
            camelExchange.getIn().setBody(body);
        }

        // propagate attachments if the data format is not POJO
        if (cxfMessage.getAttachments() != null
                && !camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class).equals(DataFormat.POJO)) {
            for (Attachment attachment : cxfMessage.getAttachments()) {
                camelExchange.getIn(AttachmentMessage.class).addAttachmentObject(attachment.getId(),
                        createCamelAttachment(attachment));
            }
        }
        addAttachmentFileCloseUoW(camelExchange, cxfExchange);
    }

    /**
     * This method is called by {@link CxfConsumer} to populate a CXF response protocol headers from a Camel exchange
     * headers before CheckError. Ensure can send protocol headers back even error/exception thrown
     */
    public void populateCxfHeaderFromCamelExchangeBeforeCheckError(
            Exchange camelExchange,
            org.apache.cxf.message.Exchange cxfExchange) {

        if (cxfExchange.isOneWay()) {
            return;
        }

        // create response context
        Map<String, Object> responseContext = new HashMap<>();

        org.apache.camel.Message response;
        if (camelExchange.getPattern().isOutCapable()) {
            if (camelExchange.getMessage() != null) {
                response = camelExchange.getMessage();
                LOG.trace("Get the response from the out message");
            } else { // Take the in message as a fall back
                response = camelExchange.getIn();
                LOG.trace("Get the response from the in message as a fallback");
            }
        } else {
            response = camelExchange.getIn();
            LOG.trace("Get the response from the in message");
        }

        // propagate response context
        Map<String, Object> camelHeaders = response.getHeaders();
        extractInvocationContextFromCamel(camelExchange, camelHeaders,
                responseContext, CxfConstants.RESPONSE_CONTEXT);

        propagateHeadersFromCamelToCxf(camelExchange, camelHeaders, cxfExchange,
                responseContext);
        if (cxfExchange.getOutMessage() != null) {
            cxfExchange.getOutMessage().put(CxfConstants.PROTOCOL_HEADERS, responseContext.get(CxfConstants.PROTOCOL_HEADERS));
        }
    }

    /**
     * This method is called by {@link CxfConsumer} to populate a CXF response exchange from a Camel exchange.
     */
    @Override
    public void populateCxfResponseFromExchange(
            Exchange camelExchange,
            org.apache.cxf.message.Exchange cxfExchange) {

        if (cxfExchange.isOneWay()) {
            return;
        }

        // create response context
        Map<String, Object> responseContext = new HashMap<>();

        org.apache.camel.Message response;
        if (camelExchange.getPattern().isOutCapable()) {
            if (camelExchange.getMessage() != null) {
                response = camelExchange.getMessage();
                LOG.trace("Get the response from the out message");
            } else { // Take the in message as a fall back
                response = camelExchange.getIn();
                LOG.trace("Get the response from the in message as a fallback");
            }
        } else {
            response = camelExchange.getIn();
            LOG.trace("Get the response from the in message");
        }

        // propagate response context
        Map<String, Object> camelHeaders = response.getHeaders();
        extractInvocationContextFromCamel(camelExchange, camelHeaders,
                responseContext, CxfConstants.RESPONSE_CONTEXT);

        propagateHeadersFromCamelToCxf(camelExchange, camelHeaders, cxfExchange,
                responseContext);
        // create out message
        Endpoint ep = cxfExchange.get(Endpoint.class);
        Message outMessage = ep.getBinding().createMessage();
        if (cxfExchange.getInMessage() instanceof SoapMessage) {
            SoapVersion soapVersion = ((SoapMessage) cxfExchange.getInMessage()).getVersion();
            ((SoapMessage) outMessage).setVersion(soapVersion);
        }

        cxfExchange.setOutMessage(outMessage);

        DataFormat dataFormat = camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY,
                DataFormat.class);

        // make sure the "requestor role" property does not get propagated as we do switch role
        responseContext.remove(Message.REQUESTOR_ROLE);

        outMessage.putAll(responseContext);

        // Do we still need to put the response context back like this
        outMessage.put(CxfConstants.RESPONSE_CONTEXT, responseContext);

        LOG.trace("Set out response context = {}", responseContext);

        // set body
        Object outBody = DefaultCxfBinding.getBodyFromCamel(response, dataFormat);

        if (outBody != null) {
            if (dataFormat == DataFormat.PAYLOAD) {
                CxfPayload<?> payload = (CxfPayload<?>) outBody;
                outMessage.setContent(List.class, getResponsePayloadList(cxfExchange, payload.getBodySources()));
                outMessage.put(Header.HEADER_LIST, payload.getHeaders());
            } else {
                if (responseContext.get(Header.HEADER_LIST) != null) {
                    outMessage.put(Header.HEADER_LIST, responseContext.get(Header.HEADER_LIST));
                }

                MessageContentsList resList = null;
                // Create a new MessageContentsList to avoid OOM from the HolderOutInterceptor
                if (outBody instanceof List) {
                    resList = new MessageContentsList((List<?>) outBody);
                } else if (outBody.getClass().isArray()) {
                    resList = new MessageContentsList((Object[]) outBody);
                } else {
                    resList = new MessageContentsList(outBody);
                }

                if (resList != null) {
                    outMessage.setContent(List.class, resList);
                    LOG.trace("Set Out CXF message content = {}", resList);
                }
            }
        } else if (!cxfExchange.isOneWay()
                && cxfExchange.getInMessage() != null
                && PropertyUtils
                        .isTrue(cxfExchange.getInMessage().getContextualProperty("jaxws.provider.interpretNullAsOneway"))) {
            // treat this non-oneway call as oneway when the provider returns a null
            changeToOneway(cxfExchange);
            return;
        }

        // propagate attachments
        Set<Attachment> attachments = null;
        boolean isXop = Boolean.valueOf(camelExchange.getProperty(Message.MTOM_ENABLED, String.class));
        if (camelExchange.getMessage() != null && camelExchange.getMessage(AttachmentMessage.class).hasAttachments()) {
            for (Map.Entry<String, org.apache.camel.attachment.Attachment> entry : camelExchange
                    .getMessage(AttachmentMessage.class)
                    .getAttachmentObjects().entrySet()) {
                if (attachments == null) {
                    attachments = new HashSet<>();
                }
                AttachmentImpl attachment = new AttachmentImpl(entry.getKey());
                org.apache.camel.attachment.Attachment camelAttachment = entry.getValue();
                attachment.setDataHandler(camelAttachment.getDataHandler());
                for (String name : camelAttachment.getHeaderNames()) {
                    attachment.setHeader(name, camelAttachment.getHeader(name));
                }
                attachment.setXOP(isXop);
                attachments.add(attachment);
            }
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
    protected void setCharsetWithContentType(Exchange camelExchange) {
        // setup the charset from content-type header
        String contentTypeHeader = ExchangeHelper.getContentType(camelExchange);
        if (contentTypeHeader != null) {
            String charset = HttpHeaderHelper.findCharset(contentTypeHeader);
            String normalizedEncoding = HttpHeaderHelper.mapCharset(charset, StandardCharsets.UTF_8.name());
            if (normalizedEncoding != null) {
                camelExchange.setProperty(ExchangePropertyKey.CHARSET_NAME, normalizedEncoding);
            }
        }
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    // Non public methods
    // -------------------------------------------------------------------------

    protected MessageContentsList getResponsePayloadList(
            org.apache.cxf.message.Exchange exchange,
            List<Source> elements) {
        BindingOperationInfo boi = exchange.getBindingOperationInfo();

        if (boi != null && boi.isUnwrapped()) {
            boi = boi.getWrappedOperation();
            exchange.put(BindingOperationInfo.class, boi);
        }

        MessageContentsList answer = new MessageContentsList();

        int i = 0;
        if (boi != null && boi.getOutput() != null) {
            for (MessagePartInfo partInfo : boi.getOutput().getMessageParts()) {
                if (elements != null && elements.size() > i) {
                    answer.put(partInfo, elements.get(i++));
                }
            }
        }

        return answer;

    }

    /**
     * @param camelExchange
     * @param cxfContext    Request or Response context
     * @param camelHeaders
     * @param contextKey
     */
    @SuppressWarnings("unchecked")
    protected void extractInvocationContextFromCamel(
            Exchange camelExchange,
            Map<String, Object> camelHeaders, Map<String, Object> cxfContext,
            String contextKey) {

        // extract from header
        Map<String, ?> context = null;
        if (camelHeaders.get(contextKey) instanceof Map) {
            context = (Map<String, ?>) camelHeaders.get(contextKey);
        }

        if (context != null) {
            cxfContext.putAll(context);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Propagate {} from header context = {}",
                        contextKey, (context instanceof WrappedMessageContext)
                                ? ((WrappedMessageContext) context).getWrappedMap()
                                : context);
            }
        }

        // extract from exchange property
        if (camelExchange.getProperty(contextKey) instanceof Map) {
            context = (Map<String, ?>) camelExchange.getProperty(contextKey);
        }

        if (context != null) {
            cxfContext.putAll(context);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Propagate {} from exchange property context = {}",
                        contextKey, (context instanceof WrappedMessageContext)
                                ? ((WrappedMessageContext) context).getWrappedMap()
                                : context);
            }
        }

        // copy camel exchange properties into context
        if (camelExchange.getProperties() != null) {
            cxfContext.putAll(camelExchange.getProperties());
        }

    }

    /**
     * @param cxfMessage
     * @param camelMessage
     * @param exchange     provides context for filtering
     */
    protected void propagateHeadersFromCxfToCamel(
            Message cxfMessage,
            org.apache.camel.Message camelMessage, Exchange exchange) {
        Map<String, List<String>> cxfHeaders = CastUtils.cast((Map<?, ?>) cxfMessage.get(CxfConstants.PROTOCOL_HEADERS));
        Map<String, Object> camelHeaders = camelMessage.getHeaders();
        camelHeaders.put(CxfConstants.CAMEL_CXF_MESSAGE, cxfMessage);

        // Copy the http header to CAMEL as we do in camel-cxfrs
        CxfHeaderHelper.copyHttpHeadersFromCxfToCamel(headerFilterStrategy, cxfMessage, camelMessage, exchange);

        if (cxfHeaders != null) {
            for (Map.Entry<String, List<String>> entry : cxfHeaders.entrySet()) {
                if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(),
                        entry.getValue(), exchange)) {
                    // We need to filter the content type with multi-part,
                    // as the multi-part stream is already consumed by AttachmentInInterceptor,
                    // it will cause some trouble when route this message to another CXF endpoint.

                    if ("Content-Type".compareToIgnoreCase(entry.getKey()) == 0
                            && entry.getValue().get(0) != null
                            && entry.getValue().get(0).startsWith("multipart/related")) {
                        // We need to keep the Content-Type if the data format is RAW message
                        DataFormat dataFormat = exchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class);
                        if (dataFormat.equals(DataFormat.RAW)) {
                            camelHeaders.put(entry.getKey(), getContentTypeString(entry.getValue()));
                        } else {
                            String contentType = replaceMultiPartContentType(entry.getValue().get(0));
                            LOG.trace("Find the multi-part Conent-Type, and replace it with {}", contentType);
                            camelHeaders.put(entry.getKey(), contentType);
                        }
                    } else if (SoapBindingConstants.SOAP_ACTION.compareToIgnoreCase(entry.getKey()) == 0
                            && entry.getValue().get(0) != null) {
                        String soapAction = entry.getValue().get(0);
                        // SOAPAction header may contain quoted value. Remove the quotes here.
                        soapAction = StringHelper.removeLeadingAndEndingQuotes(soapAction);
                        camelHeaders.put(SoapBindingConstants.SOAP_ACTION, soapAction);
                    } else {
                        LOG.trace("Populate header from CXF header={} value={}",
                                entry.getKey(), entry.getValue());
                        List<String> values = entry.getValue();
                        Object evalue;
                        if (values.size() > 1) {
                            final boolean headersMerged
                                    = exchange.getProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.FALSE,
                                            Boolean.class);
                            if (headersMerged) {
                                StringBuilder sb = new StringBuilder();
                                for (Iterator<String> it = values.iterator(); it.hasNext();) {
                                    sb.append(it.next());
                                    if (it.hasNext()) {
                                        sb.append(',').append(' ');
                                    }
                                }
                                evalue = sb.toString();
                            } else {
                                evalue = values;
                            }
                        } else if (values.size() == 1) {
                            evalue = values.get(0);
                        } else {
                            evalue = null;
                        }
                        if (evalue != null) {
                            camelHeaders.put(entry.getKey(), evalue);
                        }
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
                LOG.trace("Populate header from CXF header={} value={}", key, value);
            } else {
                ((List<?>) value).clear();
            }
        }

        // propagate the SOAPAction header
        String soapAction = (String) camelHeaders.get(SoapBindingConstants.SOAP_ACTION);
        // Remove SOAPAction from the protocol header, as it will not be overrided
        if (ObjectHelper.isEmpty(soapAction) || "\"\"".equals(soapAction)) {
            camelHeaders.remove(SoapBindingConstants.SOAP_ACTION);
        }
        soapAction = (String) cxfMessage.get(SoapBindingConstants.SOAP_ACTION);
        if (soapAction != null) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(SoapBindingConstants.SOAP_ACTION, soapAction, exchange)) {
                camelHeaders.put(SoapBindingConstants.SOAP_ACTION, soapAction);
                LOG.trace("Populate header from CXF header={} value={}", SoapBindingConstants.SOAP_ACTION, soapAction);
            }
        }

    }

    // replace the multi-part content-type
    protected String replaceMultiPartContentType(String contentType) {
        String result = "";
        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("type=")) {
                part = part.substring(5).trim();
                if (part.charAt(0) == '\"') {
                    result = part.substring(1, part.length() - 1);
                } else {
                    result = part.substring(5);
                }
                break;
            }
        }
        return result;
    }

    protected String getContentTypeString(List<String> values) {
        StringJoiner result = new StringJoiner("; ");
        for (String value : values) {
            result.add(value);
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    protected void propagateHeadersFromCamelToCxf(
            Exchange camelExchange,
            Map<String, Object> camelHeaders,
            org.apache.cxf.message.Exchange cxfExchange,
            Map<String, Object> cxfContext) {

        // get cxf transport headers (if any) from camel exchange
        // use a treemap to keep ordering and ignore key case
        Map<String, List<String>> transportHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (camelExchange != null) {
            Map<String, List<String>> h = CastUtils.cast((Map<?, ?>) camelExchange.getProperty(CxfConstants.PROTOCOL_HEADERS));
            if (h != null) {
                transportHeaders.putAll(h);
            }
        }
        Map<String, List<String>> headers = CastUtils.cast((Map<?, ?>) camelHeaders.get(CxfConstants.PROTOCOL_HEADERS));
        if (headers != null) {
            transportHeaders.putAll(headers);
        }

        // TODO camelExchange may be null
        DataFormat dataFormat = camelExchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class);

        for (Map.Entry<String, Object> entry : camelHeaders.entrySet()) {
            // put response code in request context so it will be copied to CXF message's property
            if (Message.RESPONSE_CODE.equals(entry.getKey()) || CxfConstants.HTTP_RESPONSE_CODE.equals(entry.getKey())) {
                LOG.debug("Propagate to CXF header: {} value: {}", Message.RESPONSE_CODE, entry.getValue());
                cxfContext.put(Message.RESPONSE_CODE, entry.getValue());
                continue;
            }

            // We need to copy the content-type if the dataformat is RAW
            if (Message.CONTENT_TYPE.equalsIgnoreCase(entry.getKey()) && dataFormat.equals(DataFormat.RAW)) {
                if (entry.getValue() != null) {
                    LOG.debug("Propagate to CXF header: {} value: {}", Message.CONTENT_TYPE, entry.getValue());
                    cxfContext.put(Message.CONTENT_TYPE, entry.getValue().toString());
                }
                continue;
            }

            // need to filter the User-Agent ignore the case, as CXF just check the header with "User-Agent"
            if (entry.getKey().equalsIgnoreCase("User-Agent")) {
                List<String> listValue = new ArrayList<>();
                listValue.add(entry.getValue().toString());
                transportHeaders.put("User-Agent", listValue);
            }

            // this header should be filtered, continue to the next header
            if (headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), camelExchange)) {
                continue;
            }

            LOG.debug("Propagate to CXF header: {} value: {}", entry.getKey(), entry.getValue());

            // put SOAP/protocol header list in exchange
            if (Header.HEADER_LIST.equals(entry.getKey())) {
                List<Header> headerList = (List<Header>) entry.getValue();
                for (Header header : headerList) {
                    header.setDirection(Header.Direction.DIRECTION_OUT);
                    LOG.trace("Propagate SOAP/protocol header: {} : {}", header.getName(), header.getObject());
                }

                //cxfExchange.put(Header.HEADER_LIST, headerList);
                cxfContext.put(entry.getKey(), headerList);
                continue;
            }

            if (ObjectHelper.isNotEmpty(entry.getValue())) {
                // things that are not filtered and not specifically copied will be put in transport headers
                if (entry.getValue() instanceof List) {
                    transportHeaders.put(entry.getKey(), (List<String>) entry.getValue());
                } else {
                    List<String> listValue = new ArrayList<>();
                    listValue.add(entry.getValue().toString());
                    transportHeaders.put(entry.getKey(), listValue);
                }
            }

        }

        if (transportHeaders.size() > 0) {
            List<String> soapActionList = transportHeaders.get(SoapBindingConstants.SOAP_ACTION);
            if (soapActionList != null && soapActionList.size() == 1) {
                String soapAction = soapActionList.get(0);
                if (!soapAction.isEmpty() && !soapAction.startsWith("\"")) {
                    //Per RFC, the SOAPAction HTTP header should be quoted if not empty
                    transportHeaders.put(SoapBindingConstants.SOAP_ACTION, Collections.singletonList("\"" + soapAction + "\""));
                }
            }
            cxfContext.put(CxfConstants.PROTOCOL_HEADERS, transportHeaders);
        } else {
            // no propagated transport headers does really mean no headers, not the ones
            // from the previous request or response propagated with the invocation context
            cxfContext.remove(CxfConstants.PROTOCOL_HEADERS);
        }
        if (camelHeaders.get(CxfConstants.OPERATION_NAMESPACE) == null
                && camelHeaders.get(CxfConstants.OPERATION_NAME) == null) {
            cxfContext.put(SoapBindingConstants.SOAP_ACTION, camelHeaders.get(SoapBindingConstants.SOAP_ACTION));
        }
    }

    protected static Object getContentFromCxf(Message message, DataFormat dataFormat, String encoding) {
        Set<Class<?>> contentFormats = message.getContentFormats();
        Object answer = null;
        if (contentFormats != null) {

            if (LOG.isTraceEnabled()) {
                for (Class<?> contentFormat : contentFormats) {
                    LOG.trace("Content format={} value={}",
                            contentFormat, message.getContent(contentFormat));
                }
            }

            if (dataFormat == DataFormat.POJO) {
                List<?> pojoMessageList = message.getContent(List.class);
                if (pojoMessageList != null && !pojoMessageList.isEmpty()) {
                    answer = pojoMessageList;
                }
                if (answer == null) {
                    answer = message.getContent(Object.class);
                    if (answer != null) {
                        answer = new MessageContentsList(answer);
                    }
                }
            } else if (dataFormat == DataFormat.PAYLOAD) {
                List<SoapHeader> headers = CastUtils.cast((List<?>) message.get(Header.HEADER_LIST));
                Map<String, String> nsMap = new HashMap<>();
                answer = new CxfPayload<>(headers, getPayloadBodyElements(message, nsMap), nsMap);

            } else if (dataFormat.dealias() == DataFormat.RAW) {
                answer = message.getContent(InputStream.class);
                if (answer == null) {
                    answer = message.getContent(Reader.class);
                    if (answer != null) {
                        if (encoding == null) {
                            encoding = "UTF-8";
                        }
                        LOG.trace("file encoding is = {}", encoding);
                        answer = new ReaderInputStream((Reader) answer, Charset.forName(encoding));
                    }
                }

            } else if (dataFormat.dealias() == DataFormat.CXF_MESSAGE
                    && message.getContent(List.class) != null) {
                // CAMEL-6404 added check point of message content
                // The message content of list could be null if there is a fault message is received
                answer = message.getContent(List.class).get(0);
            }

            LOG.trace("Extracted body from CXF message = {}", answer);
        }
        return answer;
    }

    protected static void addNamespace(Element element, Map<String, String> nsMap) {
        for (Map.Entry<String, String> ns : nsMap.entrySet()) {
            // We should not override the namespace setting of the element
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(ns.getKey())) {
                if (ObjectHelper.isEmpty(element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE))) {
                    element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, ns.getKey(), ns.getValue());
                }
            } else if (StringUtils.isEmpty(ns.getKey())) {
                if (ObjectHelper.isEmpty(element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE))) {
                    element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", ns.getValue());
                }
            } else {
                if (ObjectHelper.isEmpty(element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + ns.getKey()))) {
                    element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                            XMLConstants.XMLNS_ATTRIBUTE + ":" + ns.getKey(), ns.getValue());
                }
            }
        }

    }

    protected static List<Source> getPayloadBodyElements(Message message, Map<String, String> nsMap) {
        // take the namespace attribute from soap envelop
        Map<String, String> bodyNC = CastUtils.cast((Map<?, ?>) message.get("soap.body.ns.context"));
        if (bodyNC != null) {
            // if there is no Node and the addNamespaceContext option is enabled, this map is available
            nsMap.putAll(bodyNC);
        } else {
            Document soapEnv = (Document) message.getContent(Node.class);
            if (soapEnv != null) {
                NamedNodeMap attrs = soapEnv.getFirstChild().getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    Node node = attrs.item(i);
                    if (!node.getNodeValue().equals(Soap11.SOAP_NAMESPACE)
                            && !node.getNodeValue().equals(Soap12.SOAP_NAMESPACE)) {
                        nsMap.put(node.getLocalName(), node.getNodeValue());
                    }
                }
            }
        }
        MessageContentsList inObjects = MessageContentsList.getContentsList(message);
        if (inObjects == null) {
            return new ArrayList<>(0);
        }
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

        List<Source> answer = new ArrayList<>();

        for (MessagePartInfo partInfo : partInfos) {
            if (!inObjects.hasValue(partInfo)) {
                continue;
            }

            Object part = inObjects.get(partInfo);

            if (part instanceof Holder) {
                part = ((Holder<?>) part).value;
            }

            if (part instanceof Source) {
                Element element = null;
                if (part instanceof DOMSource) {
                    element = getFirstElement(((DOMSource) part).getNode());
                }

                if (element != null) {
                    addNamespace(element, nsMap);
                    answer.add(new DOMSource(element));
                } else {
                    answer.add((Source) part);
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extract body element {}",
                            element == null ? "null" : getXMLString(element));
                }
            } else if (part instanceof Element) {
                addNamespace((Element) part, nsMap);
                answer.add(new DOMSource((Element) part));
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unhandled part type '{}'", part.getClass());
                }
            }
        }

        return answer;
    }

    private static String getXMLString(Element el) {
        try {
            return StaxUtils.toString(el);
        } catch (Exception t) {
            //ignore
        }
        return "unknown content";
    }

    public static Object getBodyFromCamel(
            org.apache.camel.Message out,
            DataFormat dataFormat) {
        Object answer = null;

        if (dataFormat == DataFormat.POJO) {
            answer = out.getBody();
        } else if (dataFormat == DataFormat.PAYLOAD) {
            answer = out.getBody(CxfPayload.class);
        } else if (dataFormat.dealias() == DataFormat.RAW) {
            answer = out.getBody(InputStream.class);
        } else if (dataFormat.dealias() == DataFormat.CXF_MESSAGE) {
            answer = out.getBody();
        }
        return answer;
    }

    private static Element getFirstElement(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            return (Element) node;
        }

        return DOMUtils.getFirstElement(node);
    }

    @Override
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

    @Override
    public void extractJaxWsContext(org.apache.cxf.message.Exchange cxfExchange, Map<String, Object> context) {
        org.apache.cxf.message.Message inMessage = cxfExchange.getInMessage();
        for (Map.Entry<String, Object> entry : inMessage.entrySet()) {
            if (entry.getKey().startsWith("jakarta.xml.ws")) {
                context.put(entry.getKey(), entry.getValue());
            }
        }

    }

    //TODO replace this method with the cxf util's method when it becomes available
    private static void changeToOneway(org.apache.cxf.message.Exchange cxfExchange) {
        cxfExchange.setOneWay(true);
        Object httpresp = cxfExchange.getInMessage().get("HTTP.RESPONSE");
        if (httpresp != null) {
            try {
                Method m = findMethod(httpresp.getClass(), "setStatus", int.class);
                if (m != null) {
                    m.invoke(httpresp, 202);
                }
            } catch (Exception e) {
                LOG.warn("Unable to set the http ", e);
            }
        }
    }

    public static Method findMethod(
            Class<?> cls,
            String name,
            Class<?>... params) {
        if (cls == null) {
            return null;
        }
        for (Class<?> cs : cls.getInterfaces()) {
            if (Modifier.isPublic(cs.getModifiers())) {
                Method m = findMethod(cs, name, params);
                if (m != null && Modifier.isPublic(m.getModifiers())) {
                    return m;
                }
            }
        }
        try {
            Method m = cls.getDeclaredMethod(name, params);
            if (m != null && Modifier.isPublic(m.getModifiers())) {
                return m;
            }
        } catch (Exception e) {
            //ignore
        }
        Method m = findMethod(cls.getSuperclass(), name, params);
        if (m == null) {
            try {
                m = cls.getMethod(name, params);
            } catch (Exception e) {
                //ignore
            }
        }
        return m;
    }
}
