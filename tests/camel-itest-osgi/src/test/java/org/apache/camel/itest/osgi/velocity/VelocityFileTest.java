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
package org.apache.camel.itest.osgi.velocity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class VelocityFileTest extends VelocityTest {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("mydir");
        createDirectory("mydir");

        InputStream is = VelocityFileTest.class.getResourceAsStream("/org/apache/camel/itest/osgi/velocity/example.vm");
        File dest = new File("mydir/example.vm");
        FileOutputStream fos = new FileOutputStream(dest, false);
        IOHelper.copyAndCloseInput(is, fos);
        fos.close();

        super.setUp();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        to("velocity:file:mydir/example.vm");
            }
        };
    }

}