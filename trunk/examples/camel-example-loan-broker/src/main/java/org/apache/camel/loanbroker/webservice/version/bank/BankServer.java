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

package org.apache.camel.loanbroker.webservice.version.bank;

import javax.xml.ws.Endpoint;

import org.apache.camel.loanbroker.webservice.version.Constants;



public class BankServer {

    Endpoint endpoint1;
    Endpoint endpoint2;
    Endpoint endpoint3;

    public void start() throws Exception {
        System.out.println("Starting Bank Server");
        Object bank1 = new Bank("bank1");
        Object bank2 = new Bank("bank2");
        Object bank3 = new Bank("bank3");

        endpoint1 = Endpoint.publish(Constants.BANK1_ADDRESS, bank1);
        endpoint2 = Endpoint.publish(Constants.BANK2_ADDRESS, bank2);
        endpoint3 = Endpoint.publish(Constants.BANK3_ADDRESS, bank3);


    }

    public void stop() {
        if (endpoint1 != null) {
            endpoint1.stop();
        }
        if (endpoint2 != null) {
            endpoint2.stop();
        }
        if (endpoint3 != null) {
            endpoint3.stop();
        }
    }


    public static void main(String args[]) throws Exception {
        BankServer server = new BankServer();
        System.out.println("Server ready...");
        server.start();
        Thread.sleep(5 * 60 * 1000);
        System.out.println("Server exiting");
        server.stop();
        System.exit(0);
    }

}
