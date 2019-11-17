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
package org.apache.camel.example.cxf.provider;

import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Provider;
import javax.xml.ws.Service.Mode;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceProvider;

// START SNIPPET: e1
@WebServiceProvider()
@ServiceMode(Mode.MESSAGE)
// END SNIPPET: e1
/**
 * This class is used by Camel just for getting the endpoint configuration
 * parameters. All the requests aimed at this class are intercepted and routed
 * to the camel route specified. The route has to set the appropriate response
 * message for the service to work.
 */
// START SNIPPET: e2
public class GreeterProvider implements Provider<SOAPMessage> {

    @Override
    public SOAPMessage invoke(SOAPMessage message) {
        // Requests should not come here as the Camel route will
        // intercept the call before this is invoked.
        throw new UnsupportedOperationException("Placeholder method");
    }

}
// END SNIPPET: e2
