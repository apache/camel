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
package org.apache.camel.component.file.remote;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Unit test used for FTP wiki documentation
 */
public class FromFtpToBinarySampleTest extends CamelTestSupport {

    @Test
    public void testDummy() throws Exception {
        // this is a noop test
    }

    // START SNIPPET: e1
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // we use a delay of 60 minutes (eg. once pr. hour we poll the
                // FTP server
                long delay = 3600000;

                // from the given FTP server we poll (= download) all the files
                // from the public/reports folder as BINARY types and store this
                // as files
                // in a local directory. Camel will use the filenames from the
                // FTPServer

                // notice that the FTPConsumer properties must be prefixed with
                // "consumer." in the URL
                // the delay parameter is from the FileConsumer component so we
                // should use consumer.delay as
                // the URI parameter name. The FTP Component is an extension of
                // the File Component.
                from("ftp://tiger:scott@localhost/public/reports?binary=true&delay=" + delay).to("file://target/test-reports");
            }
        };
    }
    // END SNIPPET: e1
}
