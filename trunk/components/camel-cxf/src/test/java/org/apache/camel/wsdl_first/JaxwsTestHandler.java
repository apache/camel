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
package org.apache.camel.wsdl_first;

import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

public class JaxwsTestHandler implements SOAPHandler<SOAPMessageContext> {

    private int faultCount;
    private int messageCount;
    private int getHeadersCount;

    public int getGetHeadersCount() {
        return getHeadersCount;
    }

    public Set<QName> getHeaders() {        
        getHeadersCount++;
        return null;
    }

    public void close(MessageContext messagecontext) {
        
    }

    public boolean handleFault(SOAPMessageContext messagecontext) {
        faultCount++;
        return true;
    }

    public boolean handleMessage(SOAPMessageContext messagecontext) {
        messageCount++;
        return true;
    }

    public void reset() {
        faultCount = 0;
        messageCount = 0;
        getHeadersCount = 0;
    }
    
    public int getFaultCount() {
        return faultCount;
    }
    
    public int getMessageCount() {
        return messageCount;
    }
    
    public String toString() {
        return "faultCount=" + faultCount + ", messageCount=" 
            + messageCount + ", getHeadersCount=" + getHeadersCount;
    }
  

}
