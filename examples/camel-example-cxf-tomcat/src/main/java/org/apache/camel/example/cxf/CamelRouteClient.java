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
package org.apache.camel.example.cxf;

import org.apache.camel.example.cxf.incident.IncidentService;
import org.apache.camel.example.cxf.incident.InputReportIncident;
import org.apache.camel.example.cxf.incident.InputStatusIncident;
import org.apache.camel.example.cxf.incident.OutputReportIncident;
import org.apache.camel.example.cxf.incident.OutputStatusIncident;
import org.apache.cxf.frontend.ClientProxyFactoryBean;

public class CamelRouteClient {

    private static final String URL = "http://localhost:8080/camel-example-cxf-tomcat/webservices/incident";
    
    protected static IncidentService createCXFClient() {
        // we use CXF to create a client for us as its easier than JAXWS and works
        ClientProxyFactoryBean factory = new ClientProxyFactoryBean();
        factory.setServiceClass(IncidentService.class);
        factory.setAddress(URL);
        return (IncidentService) factory.create();
    }

    public static void main(String[] args) throws Exception {
        CamelRouteClient client = new CamelRouteClient();
        client.runTest();
    }
    
    protected void runTest() throws Exception {
       
        // create input parameter
        InputReportIncident input = new InputReportIncident();
        input.setIncidentId("123");
        input.setIncidentDate("2008-08-18");
        input.setGivenName("Claus");
        input.setFamilyName("Ibsen");
        input.setSummary("Bla");
        input.setDetails("Bla bla");
        input.setEmail("davsclaus@apache.org");
        input.setPhone("0045 2962 7576");

        // create the webservice client and send the request
        IncidentService client = createCXFClient();
        OutputReportIncident out = client.reportIncident(input);
        System.out.println(out.getCode());
        InputStatusIncident inStatus = new InputStatusIncident();
        inStatus.setIncidentId("456");
        OutputStatusIncident outStatus = client.statusIncident(inStatus);
        System.out.println(outStatus.getStatus());
       
    }

}
