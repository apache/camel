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
package org.apache.camel.component.file.remote.integration;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpProducerAllowNullBodyIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/allownull?password=admin&fileName=allowNullBody.txt";
    }

    @Test
    public void testAllowNullBodyTrue() {
        template.sendBody(getFtpUrl() + "&allowNullBody=true", null);

        assertFileExists(service.ftpFile("allownull/allowNullBody.txt"));
    }

    @Test
    public void testAllowNullBodyFalse() {
        String uri = getFtpUrl() + "&allowNullBody=false";
        Exception ex = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, null));

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, ex.getCause());
        assertTrue(cause.getMessage().endsWith("allowNullBody.txt"));

        assertFalse(service.ftpFile("allownull/allowNullBody.txt").toFile().exists(),
                "allowNullBody set to false with null body should not create a new file");
    }

}
