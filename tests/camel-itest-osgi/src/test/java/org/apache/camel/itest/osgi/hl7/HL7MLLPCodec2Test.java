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
package org.apache.camel.itest.osgi.hl7;

import ca.uhn.hl7v2.model.v24.message.QRY_A19;
import ca.uhn.hl7v2.model.v24.segment.MSH;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
@Ignore("TODO need to find a way fix the hl7codec setting issue")
public class HL7MLLPCodec2Test extends OSGiIntegrationSpringTestSupport implements Processor {

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/hl7/CamelContext2.xml"});
    }

    @Test
    public void testSendHL7Message() throws Exception {
        String line1 = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|1234|P|2.4";
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuilder in = new StringBuilder();
        in.append(line1);
        in.append("\n");
        in.append(line2);

        String out = (String)template.requestBody("mina:tcp://127.0.0.1:8889?sync=true&codec=#hl7codec", in.toString());

        String[] lines = out.split("\r");
        assertEquals("MSH|^~\\&|MYSENDER||||200701011539||ADR^A19||||123", lines[0]);
        assertEquals("MSA|AA|123", lines[1]);
    }

    public void process(Exchange exchange) throws Exception {
        QRY_A19 a19 = exchange.getIn().getBody(QRY_A19.class);
        MSH msh = a19.getMSH();
        assertEquals("MYSENDER", msh.getSendingApplication().getHd1_NamespaceID().getValue());

        String out = "MSH|^~\\&|MYSENDER||||200701011539||ADR^A19||||123\rMSA|AA|123\n";
        exchange.getOut().setBody(out);
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
             // using the features to install the other camel components
            loadCamelFeatures("camel-hl7", "camel-mina"));

        return options;
    }

}