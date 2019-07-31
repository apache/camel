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
package org.apache.camel.component.cxf;

import java.util.Map;

import org.apache.cxf.message.Exchange;

/**
 * An strategy interface for implementing binding between CXF {@link Exchange} 
 * and Camel {@link org.apache.camel.Exchange}.  
 * 
 * Assumptions: CxfProducer and CxfConsumer set {@link DataFormat} and
 * {@link org.apache.cxf.service.model.BindingOperationInfo} 
 * in Camel Exchange property before calling into these methods.  
 * 
 * @since 2.0
 */
public interface CxfBinding {
    
    /**
     * <p>
     * Populate a CXF Exchange from a Camel Exchange.  The resulted CXF Exchange is an 
     * <b>outgoing request</b> to be sent to CXF server.  This method is called by 
     * {@link CxfProducer#process(org.apache.camel.Exchange)} to process a Camel Exchange
     * for invoking an CXF web service operation.  Note that information is populated
     * to CXF Exchange and the request context, which are passed as arguments to the 
     * CXF API's Client.invoke() method.  The arguments to the web service operation
     * are extracted from the Camel IN message body by CxfProducer.
     * </p>
     * 
     * <p>
     * Exchange is passed in this direction: Camel route => CxfProducer => <b>apply this
     * binding method </b>=> CXF server
     * </p>
     * 
     * @param cxfExchange exchange to be populated
     * @param camelExchange exchange that contains a request
     * @param requestContext a map contains request contexts.  <b>This parameter must not
     * be null</b>.  The Client.invoke() method does not allow caller to
     * pass in a CXF Message.  The request context are copied to the CXF Message by the
     * Client.invoke() method.  This is how caller can set properties on the CXF message.  
     * 
     */
    void populateCxfRequestFromExchange(Exchange cxfExchange,
            org.apache.camel.Exchange camelExchange,
            Map<String, Object> requestContext);

    /**
     * <p>
     * Populate a Camel Exchange from a CXF Exchange, which is a an <b>incoming response</b>
     * from a CXF server.  This method is called by {@link CxfProducer} after it makes an 
     * invocation to the Client.invoke() method.  It calls this method to translate the 
     * CXF response message to Camel message. 
     * </p>
     * 
     * <p>
     * Exchange is passed in this direction: Camel route <= <b>apply this binding method</b>
     * <= CxfProducer <= CXF Server
     * </p>
     * @param camelExchange exchanged to be populated 
     * @param cxfExchange exchange that contains a response
     * @param responseContext map contains response context from CXF
     */
    void populateExchangeFromCxfResponse(org.apache.camel.Exchange camelExchange,
            Exchange cxfExchange, Map<String, Object> responseContext);
  
    /**
     * <p>
     * Populate a Camel Exchange from a CXF Exchange, which is an <b>incoming request</b> 
     * from a CXF client.  This method is called by {@link CxfConsumer} to handle a 
     * CXF request arrives at an endpoint.  It translates a CXF request to a Camel
     * Exchange for Camel route to process the exchange.
     * </p>  
     * 
     * <p>
     * Exchange is passed in this direction: CXF Endpoint => CxfConsumer => <b>apply this
     * binding method </b> => Camel route
     * </p>
     * @param cxfExchange CXF exchange that contains a request
     * @param camelExchange Camel exchange to be populated
     */
    void populateExchangeFromCxfRequest(Exchange cxfExchange,
            org.apache.camel.Exchange camelExchange);
    

    /**
     * <p>
     * Populate a CXF Exchange from a Camel Exchange.  The resulted CXF Exchange is an 
     * <b>outgoing response</b> to be sent back to the CXF client.  This method is called 
     * by {@link CxfConsumer} to translate a Camel Exchange to a CXF response Exchange.
     * </p>
     * 
     * <p>
     * Exchange is passed in this direction: CXF Endpoint <= <b>apply this binding method</b>
     * <= CxfConsumer <= Camel route
     * </p>
     * @param camelExchange Camel exchange that contains an out message
     * @param cxfExchange CXF exchange to be populated
     */
    void populateCxfResponseFromExchange(org.apache.camel.Exchange camelExchange,
            Exchange cxfExchange);

    /**
     * <p>
     * Extract the message headers which key are start from javax.xml.ws* from the
     * CXF exchange's inMessage, and put these headers into the context
     * </p>
     * 
     * 
     * @param cxfExchange CXF exchange to be populated
     * @param context The map which used to store the message headers
     */
    void extractJaxWsContext(Exchange cxfExchange, Map<String, Object> context);
    
    /**
     * <p>
     * Copy the javax.xml.ws* headers into cxfExchange's outMessage, 
     * if the cxfExchange has no outMessage, skip this copy
     * </p>
     * 
     * @param cxfExchange CXF exchange to be populated
     * @param context The map which used to store the message headers
     */
    void copyJaxWsContext(Exchange cxfExchange, Map<String, Object> context);

}
