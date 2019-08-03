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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrepareRequest implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(PrepareRequest.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        List<String> params = new ArrayList<>();
        params.add(exchange.getIn().getBody(String.class));
        exchange.getOut().setBody(params);
        String operation = (String)exchange.getIn().getHeader(CxfConstants.OPERATION_NAME);
        LOG.info("The operation name is " + operation);
        exchange.getOut().setHeader(CxfConstants.OPERATION_NAME, operation);
    }

}
