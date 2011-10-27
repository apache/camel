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
package org.apache.camel.component.cxf.soap.headers;

public final class Constants {

    public static final String TEST_HDR_NS = "http://cxf.apache.org/outofband/Header";
    public static final String TEST_HDR_REQUEST_ELEM = "outofbandHeader";
    public static final String TEST_HDR_RESPONSE_ELEM = "outofbandHeader";
    public static final SOAPHeaderData IN_HEADER_DATA = new SOAPHeaderData();
    public static final SOAPHeaderData OUT_HEADER_DATA = new SOAPHeaderData();
    public static final SOAPHeaderData IN_OUT_REQUEST_HEADER_DATA = new SOAPHeaderData();
    public static final SOAPHeaderData IN_OUT_RESPONSE_HEADER_DATA = new SOAPHeaderData();
    
    static {
        IN_HEADER_DATA.setOriginator("CxfSoapHeaderRoutePropagationTest.testInHeader");        
        IN_HEADER_DATA.setMessage("Invoking CxfSoapHeaderRoutePropagationTest.testInHeader()");
        OUT_HEADER_DATA.setOriginator("CxfSoapHeaderRoutePropagationTest.testOutHeader");        
        OUT_HEADER_DATA.setMessage("Invoking CxfSoapHeaderRoutePropagationTest.testOutHeader()");
        IN_OUT_REQUEST_HEADER_DATA.setOriginator("CxfSoapHeaderRoutePropagationTest.testInOutHeader Requestor");        
        IN_OUT_REQUEST_HEADER_DATA.setMessage("Invoking CxfSoapHeaderRoutePropagationTest.testInOutHeader() Request");
        IN_OUT_RESPONSE_HEADER_DATA.setOriginator("CxfSoapHeaderRoutePropagationTest.testInOutHeader Responser");        
        IN_OUT_RESPONSE_HEADER_DATA.setMessage("Invoking CxfSoapHeaderRoutePropagationTest.testInOutHeader() Responser");
    }
    
    private Constants() {
    }
    
    public static boolean equals(SOAPHeaderData lhs, SOAPHeaderData rhs) {
        if (compare(lhs, rhs)) {
            return true;
        }
        if (compare(lhs.getMessage(), rhs.getMessage())) {
            return true;
        }
        if (compare(lhs.getOriginator(), rhs.getOriginator())) {
            return true;
        }
        return false;
    }
    
    public static <L, R> boolean compare(L lhs, R rhs) {
        if (lhs == rhs) {
            return true;
        }
        if (lhs == null || rhs == null && lhs != rhs) {
            return false;
        }
        return lhs.equals(rhs);        
    }
}
