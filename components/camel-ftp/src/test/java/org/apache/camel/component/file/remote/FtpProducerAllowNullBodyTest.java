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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.Test;

public class FtpProducerAllowNullBodyTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/allownull?password=admin&fileName=allowNullBody.txt";
    }

    @Test
    public void testAllowNullBodyTrue() throws Exception {
        template.sendBody(getFtpUrl() + "&allowNullBody=true", null);

        assertFileExists(FTP_ROOT_DIR + "/allownull/allowNullBody.txt");
    }

    @Test
    public void testAllowNullBodyFalse() throws Exception {
        try {
            template.sendBody(getFtpUrl() + "&allowNullBody=false", null);
            fail("Should have thrown a GenericFileOperationFailedException");
        } catch (CamelExecutionException e) {
            GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
            assertTrue(cause.getMessage().endsWith("allowNullBody.txt"));
        }

        assertFalse("allowNullBody set to false with null body should not create a new file", new File(FTP_ROOT_DIR + "/allownull/allowNullBody.txt").exists());
    }

}