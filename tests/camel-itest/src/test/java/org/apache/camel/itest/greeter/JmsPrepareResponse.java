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
package org.apache.camel.itest.greeter;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.hello_world_soap_http.PingMeFault;
import org.apache.hello_world_soap_http.types.FaultDetail;

public class JmsPrepareResponse implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        if ("greetMe".equals(in.getHeader(CxfConstants.OPERATION_NAME))) {
            String request = in.getBody(String.class);               
            exchange.getOut().setBody("Hello" + request);
        } else {
            // throw the Exception
            FaultDetail faultDetail = new FaultDetail();
            faultDetail.setMajor((short)2);
            faultDetail.setMinor((short)1);
            exchange.getOut().setBody(new PingMeFault("PingMeFault raised by server", faultDetail));          
        }
    }
}
