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
package org.apache.camel.component.smb;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmbProducerDoneFileNameIT extends SmbServerTestSupport {

    protected String getSmbUrl(String path) {
        return String.format(
                "smb:%s/%s/%s?username=%s&password=%s",
                service.address(), service.shareName(), path, service.userName(), service.password());
    }

    @Test
    public void testProducerConstantDoneFileName() {
        template.sendBodyAndHeader(getSmbUrl("constdone") + "&doneFileName=done", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World",
                        new String(copyFileContentFromContainer("data/rw/constdone/hello.txt"))));

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("",
                        new String(copyFileContentFromContainer("/data/rw/constdone/done"))));
    }

    @Test
    public void testProducerPrefixDoneFileName() {
        template.sendBodyAndHeader(getSmbUrl("prefixdone") + "&doneFileName=done-${file:name}", "Hello World",
                Exchange.FILE_NAME,
                "hello.txt");

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World",
                        new String(copyFileContentFromContainer("/data/rw/prefixdone/hello.txt"))));

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("",
                        new String(copyFileContentFromContainer("/data/rw/prefixdone/done-hello.txt"))));
    }

    @Test
    public void testProducerExtDoneFileName() {
        template.sendBodyAndHeader(getSmbUrl("extdone") + "&doneFileName=${file:name}.done", "Hello World",
                Exchange.FILE_NAME,
                "hello.txt");

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World",
                        new String(copyFileContentFromContainer("/data/rw/extdone/hello.txt"))));

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("",
                        new String(copyFileContentFromContainer("/data/rw/extdone/hello.txt.done"))));
    }

    @Test
    public void testProducerReplaceExtDoneFileName() {
        template.sendBodyAndHeader(getSmbUrl("replextdone") + "&doneFileName=${file:name.noext}.done", "Hello World",
                Exchange.FILE_NAME,
                "hello.txt");

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World",
                        new String(copyFileContentFromContainer("/data/rw/replextdone/hello.txt"))));

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("",
                        new String(copyFileContentFromContainer("/data/rw/replextdone/hello.done"))));
    }

    @Test
    public void testProducerInvalidDoneFileName() {
        String uri = getSmbUrl("invaliddone") + "&doneFileName=${file:parent}/foo";

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt"));

        ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class,
                ex.getCause());

        assertTrue(cause.getMessage().endsWith("Cannot resolve reminder: ${file:parent}/foo"), cause.getMessage());
    }

    @Test
    public void testProducerEmptyDoneFileName() {
        String uri = getSmbUrl("emptydone") + "&doneFileName=";
        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt"));

        IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(cause.getMessage().startsWith("doneFileName must be specified and not empty"), cause.getMessage());
    }
}
