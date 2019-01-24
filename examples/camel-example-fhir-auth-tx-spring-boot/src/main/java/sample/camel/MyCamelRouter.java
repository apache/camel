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
package sample.camel;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.ProtocolException;
import org.hl7.fhir.dstu3.model.Patient;
import org.springframework.stereotype.Component;

/**
 * A simple Camel route that triggers from a file and pushes to a FHIR server.
 * <p/>
 * Use <tt>@Component</tt> to make Camel auto detect this route when starting.
 */
@Component
public class MyCamelRouter extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("file:{{input}}").routeId("fhir-example")
            .onException(ProtocolException.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Error connecting to FHIR server with URL:{{serverUrl}}, please check the application.properties file ${exception.message}")
            .end()
            .log("Converting ${file:name}")
            .unmarshal().csv()
            .process(exchange -> {
                List<Patient> bundle = new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<List<String>> patients = (List<List<String>>) exchange.getIn().getBody();
                for (List<String> patient: patients) {
                    Patient fhirPatient = new Patient();
                    fhirPatient.setId(patient.get(0));
                    fhirPatient.addName().addGiven(patient.get(1));
                    fhirPatient.getNameFirstRep().setFamily(patient.get(2));
                    bundle.add(fhirPatient);
                }
                exchange.getIn().setBody(bundle);
            })
            // create Patient in our FHIR server
            .to("fhir://transaction/withResources?inBody=resources&serverUrl={{serverUrl}}&username={{serverUser}}&password={{serverPassword}}&fhirVersion={{fhirVersion}}")
            // log the outcome
            .log("Patients created successfully: ${body}");
    }

}
