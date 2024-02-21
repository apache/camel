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
package org.apache.camel.component.irc.it;

import org.apache.camel.BindToRegistry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Disabled;

@Disabled
public class IrcsWithSslContextParamsRouteIT extends IrcRouteIT {

    // TODO This test is disabled until we can find a public SSL enabled IRC
    // server to test against. To use this test, follow the following procedures:
    // 1) Download and install UnrealIRCd 3.2.9 from http://www.unrealircd.com/
    // 2) Copy the contents of the src/test/unrealircd folder into the installation
    //    folder of UnrealIRCd.
    // 3) Start UnrealIRCd and execute this test.  Often the test executes quicker than
    //    the IRC server responds and the assertion will fail.  In order to get the test to
    //    pass reliably, you may need to set a break point in IrcEndpoint#joinChanel in order
    //    to slow the route creation down enough for the event listener to be in place
    //    when camel-con joins the room.

    @BindToRegistry("sslContextParameters")
    protected SSLContextParameters loadSslContextParams() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("localhost.p12");
        ksp.setPassword("changeit");

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    @Override
    protected String sendUri() {
        return "ircs://camel-prd-user@localhost:6669/#camel-test?nickname=camel-prd&password=password&sslContextParameters=#sslContextParameters";
    }

    @Override
    protected String fromUri() {
        return "ircs://camel-con-user@localhost:6669/#camel-test?nickname=camel-con&password=password&sslContextParameters=#sslContextParameters";
    }

}
