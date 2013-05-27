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
package org.apache.camel.component.sap.netweaver;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

public class NetWeaverProducer extends DefaultProducer {

    private Producer http;

    private String url = "https://sapes1.sapdevcenter.com/sap/opu/odata/IWBEP/RMTSAMPLEFLIGHT_2/"
            + "FlightCollection(AirLineID='AA',FlightConnectionID='0017',FlightDate=datetime'2012-08-29T00%3A00%3A00')/FlightBooking";

    public NetWeaverProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public NetWeaverEndpoint getEndpoint() {
        return (NetWeaverEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // call net weaver, and use json data format

        Exchange dummy = getEndpoint().createExchange();
        dummy.getIn().setHeader("Accept", "application/json");
        log.info("Calling SAP Net-Weaver");
        http.process(dummy);

        String json = dummy.hasOut() ? dummy.getOut().getBody(String.class) : dummy.getIn().getBody(String.class);
        System.out.println(json);

        ObjectMapper mapper = new ObjectMapper();
        Map map = mapper.readValue(json, Map.class);
        System.out.println(map);

        exchange.getIn().setBody(map);
    }

    @Override
    protected void doStart() throws Exception {
        String s = url + "?authUsername=" + getEndpoint().getUsername() + "&authPassword=" + getEndpoint().getPassword() + "&authMethod=Basic";
        log.info("Using url: {}", s);
        http = getEndpoint().getCamelContext().getEndpoint(s).createProducer();

        ServiceHelper.startService(http);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(http);
    }
}
