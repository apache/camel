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
package org.apache.camel.component.ode;

import java.io.File;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test which let Camel invoke a bpel process
 *
 * @version $Revision$
 */
public class OdeCamelHelloWorldTest extends CamelTestSupport {

    @Test
    public void testOde() throws Exception {
        OdeService service = new OdeService(context);
        File dir = new File("").getAbsoluteFile();
        String path = dir.getAbsolutePath() + "/target/test-classes";
        String work = dir.getAbsolutePath() + "/target";
        service.setInstallRoot(path);
        service.setWorkRoot(work);
        service.start();

        System.out.println("=============================");
        String deploy = "bpel/HelloWorld2";
        service.deployBpel(deploy);
        System.out.println("=============================");
        String reply = service.invokeSomething("Bye");
        System.out.println("=============================");
        service.stop();

        assertEquals("<message><TestPart>Bye World</TestPart></message>", reply);
    }

}
