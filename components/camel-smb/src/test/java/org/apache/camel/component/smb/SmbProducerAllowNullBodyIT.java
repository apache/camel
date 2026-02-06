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
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmbProducerAllowNullBodyIT extends SmbServerTestSupport {

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/nullbody?username=%s&password=%s",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testAllowNullBodyTrue() {
        template.sendBody(getSmbUrl() + "&fileName=allowNullBody1.txt&allowNullBody=true", null);

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("",
                        new String(copyFileContentFromContainer("/data/rw/nullbody/allowNullBody1.txt"))));
    }

    @Test
    public void testAllowNullBodyFalse() {
        String uri = getSmbUrl() + "&fileName=allowNullBody2.txt&allowNullBody=false";
        Exception ex = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, null));

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, ex.getCause());
        assertTrue(cause.getMessage().endsWith("allowNullBody2.txt"));

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull((copyFileContentFromContainer("/data/rw/nullbody/allowNullBody2.txt"))));
    }

}
