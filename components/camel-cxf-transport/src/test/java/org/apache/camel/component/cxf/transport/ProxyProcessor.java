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

package org.apache.camel.component.cxf.transport;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyProcessor implements Processor {
    private static final transient Logger LOG = LoggerFactory.getLogger(ProxyProcessor.class);
    
    private HelloService proxy;
    
    public void setHelloService(HelloService service) {
        proxy = service; 
    }

    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        String request = in.getBody(String.class);
        LOG.info("Get the request " + request);
        // the jbi service processor will ignore the parameter
        String response = proxy.echo(request);
        LOG.info("Get the response " + response);
        exchange.getOut().setBody(response);
        
    }

}
