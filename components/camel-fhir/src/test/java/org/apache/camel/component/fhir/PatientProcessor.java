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
package org.apache.camel.component.fhir;

import java.util.List;
import ca.uhn.fhir.model.base.resource.BaseOperationOutcome;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IClientExecutable;
import ca.uhn.hl7v2.model.v24.message.ORU_R01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hl7.fhir.dstu3.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Patient processor that converts the Patient segment of a {@link ORU_R01} message into a FHIR dtsu3 {@link Patient}.
 */
public class PatientProcessor implements Processor {
    private final Logger log = LoggerFactory.getLogger(PatientProcessor.class.getName());
    private final IGenericClient client;
    private final CamelContext camelContext;

    public PatientProcessor(IGenericClient client, CamelContext camelContext) {
        this.client = client;
        this.camelContext = camelContext;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ORU_R01 msg = exchange.getIn().getBody(ORU_R01.class);
        //map to Patient
        Patient patient = getPatient(msg);
        log.info("Mapped Dtsu3 patient {}", camelContext.getTypeConverter().convertTo(String.class, exchange, patient));
        final IClientExecutable clientExecutable = client.create().resource(patient).prefer(PreferReturnEnum.REPRESENTATION);
        Object result = clientExecutable.execute();
        if (result instanceof BaseOperationOutcome) {
            List<? extends BaseOperationOutcome.BaseIssue> issues = ((BaseOperationOutcome) result).getIssue();
            if (!issues.isEmpty()) {
                exchange.setException(new Exception("Error executing client operation: " + issues.get(0).getDetailsElement().getValue()));
            }
        }
        exchange.getIn().setBody(result);
    }

    /**
     * Converts {@link ORU_R01} to {@link Patient}
     */
    private Patient getPatient(ORU_R01 msg) {
        Patient patient = new Patient();
        final PID pid = msg.getPATIENT_RESULT().getPATIENT().getPID();
        String surname = pid.getPatientName()[0].getFamilyName().getFn1_Surname().getValue();
        String name = pid.getPatientName()[0].getGivenName().getValue();
        String patientId = msg.getPATIENT_RESULT().getPATIENT().getPID().getPatientID().getCx1_ID().getValue();
        patient.addName()
            .addGiven(name);
        patient.getNameFirstRep().setFamily(surname);
        patient.setId(patientId);
        return patient;
    }
}
