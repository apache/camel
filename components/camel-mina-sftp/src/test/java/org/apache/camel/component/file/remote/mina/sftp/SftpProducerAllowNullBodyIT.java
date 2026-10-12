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
package org.apache.camel.component.file.remote.mina.sftp;

import java.io.File;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
class SftpProducerAllowNullBodyIT extends SftpServerTestSupport {

    private String getSftpUri(String fileName) {
        return "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/allownull?username=admin&password=admin"
               + "&fileName=" + fileName + "&knownHostsFile=" + service.getKnownHostsFile();
    }

    @Test
    void testAllowNullBodyTrue() {
        template.sendBody(getSftpUri("allowNullBodyTrue.txt") + "&allowNullBody=true", null);

        File file = ftpFile("allownull/allowNullBodyTrue.txt").toFile();
        assertTrue(file.exists(), "allowNullBody set to true with null body should create an empty file");
        assertEquals(0, file.length());
    }

    @Test
    void testAllowNullBodyFalse() {
        String uri = getSftpUri("allowNullBodyFalse.txt") + "&allowNullBody=false";
        Exception ex = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, null));

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, ex.getCause());
        assertTrue(cause.getMessage().startsWith("Cannot write null body to file:"),
                "expected the explicit null body error, but was: " + cause.getMessage());
        assertTrue(cause.getMessage().endsWith("allowNullBodyFalse.txt"), cause.getMessage());

        assertFalse(ftpFile("allownull/allowNullBodyFalse.txt").toFile().exists(),
                "allowNullBody set to false with null body should not create a new file");
    }
}
