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
package org.apache.camel.component.cxf.interceptors;

import java.util.Iterator;
import java.util.Map;

import org.w3c.dom.Document;

import org.apache.cxf.binding.soap.interceptor.EndpointSelectionInterceptor;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.common.util.UrlUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.WSDLGetUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.OutgoingChainInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * Just copy the from WSDLGetInterceptor to provide backward compatible support for 2.7.x
 */
public class RawMessageWSDLGetInterceptor extends AbstractPhaseInterceptor<Message> {
    public static final RawMessageWSDLGetInterceptor INSTANCE = new RawMessageWSDLGetInterceptor();
    public static final String DOCUMENT_HOLDER = RawMessageWSDLGetInterceptor.class.getName() + ".documentHolder";

    public RawMessageWSDLGetInterceptor() {
        super(Phase.READ);
        getAfter().add(EndpointSelectionInterceptor.class.getName());
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        String method = (String) message.get(Message.HTTP_REQUEST_METHOD);
        String query = (String) message.get(Message.QUERY_STRING);

        if (!"GET".equals(method) || StringUtils.isEmpty(query)) {
            return;
        }

        String baseUri = (String) message.get(Message.REQUEST_URL);
        String ctx = (String) message.get(Message.PATH_INFO);

        Map<String, String> map = UrlUtils.parseQueryString(query);
        if (isRecognizedQuery(map)) {
            Document doc = getDocument(message, baseUri, map, ctx);

            Endpoint e = message.getExchange().get(Endpoint.class);
            Message mout = new MessageImpl();
            mout.setExchange(message.getExchange());
            mout = e.getBinding().createMessage(mout);
            mout.setInterceptorChain(OutgoingChainInterceptor.getOutInterceptorChain(message.getExchange()));
            message.getExchange().setOutMessage(mout);

            mout.put(DOCUMENT_HOLDER, doc);

            Iterator<Interceptor<? extends Message>> iterator = mout.getInterceptorChain().iterator();
            while (iterator.hasNext()) {
                Interceptor<? extends Message> inInterceptor = iterator.next();
                if (inInterceptor instanceof AbstractPhaseInterceptor) {
                    AbstractPhaseInterceptor<?> interceptor = (AbstractPhaseInterceptor<?>) inInterceptor;
                    if (interceptor.getPhase().equals(Phase.PREPARE_SEND)
                            || interceptor.getPhase().equals(Phase.PRE_STREAM)) {
                        // just make sure we keep the right interceptors
                        continue;
                    }
                }
                mout.getInterceptorChain().remove(inInterceptor);
            }

            // notice this is being added after the purge above, don't swap the order!
            mout.getInterceptorChain().add(RawMessageWSDLGetOutInterceptor.INSTANCE);

            // skip the service executor and goto the end of the chain.
            message.getInterceptorChain().doInterceptStartingAt(
                    message,
                    OutgoingChainInterceptor.class.getName());
        }
    }

    private Document getDocument(Message message, String base, Map<String, String> params, String ctxUri) {
        // cannot have two wsdl's being generated for the same endpoint at the same
        // time as the addresses may get mixed up
        // For WSDL's the WSDLWriter does not share any state between documents.
        // For XSD's, the WSDLGetUtils makes a copy of any XSD schema documents before updating
        // any addresses and returning them, so for both WSDL and XSD this is the only part that needs
        // to be synchronized.
        synchronized (message.getExchange().getEndpoint()) {
            return new WSDLGetUtils().getDocument(message, base, params, ctxUri,
                    message.getExchange().getEndpoint().getEndpointInfo());
        }
    }

    private boolean isRecognizedQuery(Map<String, String> map) {
        if (map.containsKey("wsdl") || map.containsKey("xsd")) {
            return true;
        }
        return false;
    }
}
