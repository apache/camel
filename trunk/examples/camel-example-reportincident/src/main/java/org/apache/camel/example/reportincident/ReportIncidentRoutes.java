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
package org.apache.camel.example.reportincident;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.bean.BeanLanguage;

/**
 * Our routes that we can build using Camel DSL as we extend the RouteBuilder class.
 * <p/>
 * In the configure method we have all kind of DSL methods we use for expressing our routes.
 */
public class ReportIncidentRoutes extends RouteBuilder {
    private boolean usingServletTransport = true;
    
    public void setUsingServletTransport(boolean flag) {
        usingServletTransport = flag;
    }

    public void configure() throws Exception {
        // webservice response for OK
        OutputReportIncident ok = new OutputReportIncident();
        ok.setCode("0");

        // endpoint to our CXF webservice  
        // We should use the related path to publish the service, when using the ServletTransport
        // so we need to configure set the bus which is configured to use the ServletTransport
        String cxfEndpointAddress = "cxf:/incident?bus=#cxf&";
        // Using the full http address for stand alone running
        if (!usingServletTransport) {
            cxfEndpointAddress = "cxf://http://localhost:{{port}}/camel-example-reportincident/webservices/incident?";
        }
        String cxfEndpoint = cxfEndpointAddress
                + "serviceClass=org.apache.camel.example.reportincident.ReportIncidentEndpoint"
                + "&wsdlURL=etc/report_incident.wsdl";

        // first part from the webservice -> file backup
        from(cxfEndpoint)
            // we need to convert the CXF payload to InputReportIncident that FilenameGenerator and velocity expects
            .convertBodyTo(InputReportIncident.class)
            // then set the file name using the FilenameGenerator bean
            .setHeader(Exchange.FILE_NAME, BeanLanguage.bean(FilenameGenerator.class, "generateFilename"))
            // and create the mail body using velocity template
            .to("velocity:etc/MailBody.vm")
            // and store the file
            .to("file://target/subfolder")
            // return OK as response
            .log("Wrote ${file:name} and returning OK response")
            .transform(constant(ok));

        // second part from the file backup -> send email
        from("file://target/subfolder")
            // set the subject of the email
            .setHeader("subject", constant("new incident reported"))
            // send the email
            .log("Sending email to incident@mycompany.com:\n${body}")
            .to("smtp://someone@localhost?password=secret&to=incident@mycompany.com");
    }
    
    public static void main(String args[]) throws Exception {
        CamelContext camel = new DefaultCamelContext();
        ReportIncidentRoutes routes = new ReportIncidentRoutes();
        routes.setUsingServletTransport(false);
        camel.addRoutes(routes);
        camel.start();
    }

}
