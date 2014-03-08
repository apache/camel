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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.converter.IOConverter;
import org.junit.Test;

public class FtpProducerFileWithPathPathSeparatorAutoNoStepwiseTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/upload?password=admin&stepwise=false&separator=Auto";
    }

    @Test
    public void testProducerFileWithPathNoStepwise() throws Exception {
        Exchange out = template.send(getFtpUrl(), new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader(Exchange.FILE_NAME, "hello/claus.txt");
            }
        });
        assertNotNull(out);

        File file = new File(FTP_ROOT_DIR + "/upload/hello/claus.txt");
        assertTrue("The uploaded file should exists", file.exists());
        assertEquals("Hello World", IOConverter.toString(file, null));

        assertEquals("upload/hello/claus.txt", out.getIn().getHeader(Exchange.FILE_NAME_PRODUCED));
    }
}