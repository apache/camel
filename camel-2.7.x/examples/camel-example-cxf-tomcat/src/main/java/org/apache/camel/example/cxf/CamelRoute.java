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
package org.apache.camel.example.cxf;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.cxf.incident.IncidentService;
import org.apache.camel.example.cxf.incident.InputReportIncident;
import org.apache.camel.example.cxf.incident.OutputReportIncident;
import org.apache.camel.example.cxf.incident.OutputStatusIncident;

// this static import is needed for older versions of Camel than 2.5
// import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * The Camel route
 *
 * @version 
 */
// START SNIPPET: e1
public class CamelRoute extends RouteBuilder {

    // CXF webservice using code first approach
    private String uri = "cxf:/incident?serviceClass=" + IncidentService.class.getName();

    @Override
    public void configure() throws Exception {
        from(uri)
            .to("log:input")
            // send the request to the route to handle the operation
            // the name of the operation is in that header
            .recipientList(simple("direct:${header.operationName}"));

        // report incident
        from("direct:reportIncident")
            .process(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    // get the id of the input
                    String id = exchange.getIn().getBody(InputReportIncident.class).getIncidentId();

                    // set reply including the id
                    OutputReportIncident output = new OutputReportIncident();
                    output.setCode("OK;" + id);
                    exchange.getOut().setBody(output);
                }
            })
            .to("log:output");

        // status incident
        from("direct:statusIncident")
            .process(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    // set reply
                    OutputStatusIncident output = new OutputStatusIncident();
                    output.setStatus("IN PROGRESS");
                    exchange.getOut().setBody(output);
                }
            })
            .to("log:output");
    }
}
// END SNIPPET: e1
