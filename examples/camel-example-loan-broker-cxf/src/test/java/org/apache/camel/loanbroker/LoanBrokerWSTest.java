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
package org.apache.camel.loanbroker;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class LoanBrokerWSTest extends CamelSpringTestSupport {

    private static String url;

    @BeforeClass
    public static void setupFreePort() throws Exception {
        // find a free port number, and write that in the custom.properties file
        // which we will use for the unit tests, to avoid port number in use problems
        int port = AvailablePortFinder.getNextAvailable();
        String bank1 = "bank1.port=" + port;
        port = AvailablePortFinder.getNextAvailable();
        String bank2 = "bank2.port=" + port;
        port = AvailablePortFinder.getNextAvailable();
        String bank3 = "bank3.port=" + port;
        port = AvailablePortFinder.getNextAvailable();
        String credit = "credit_agency.port=" + port;
        port = AvailablePortFinder.getNextAvailable();
        String loan = "loan_broker.port=" + port;

        File custom = new File("target/custom.properties");
        FileOutputStream fos = new FileOutputStream(custom);
        fos.write(bank1.getBytes());
        fos.write("\n".getBytes());
        fos.write(bank2.getBytes());
        fos.write("\n".getBytes());
        fos.write(bank3.getBytes());
        fos.write("\n".getBytes());
        fos.write(credit.getBytes());
        fos.write("\n".getBytes());
        fos.write(loan.getBytes());
        fos.write("\n".getBytes());
        fos.close();

        url = "http://localhost:" + port + "/loanBroker";
    }

    @Test
    public void testInvocation() throws Exception {
        LoanBrokerWS loanBroker = Client.getProxy(url);
        String result = loanBroker.getLoanQuote("SSN", 1000.54, 10);
        log.info("Result: {}", result);
        assertTrue(result.startsWith("The best rate is [ssn:SSN bank:bank"));
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/webServiceCamelContext.xml");
    }
}
