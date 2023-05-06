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
package org.apache.camel.component.as2.api.util;

import java.nio.charset.StandardCharsets;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.http.Header;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EntityUtilsTest {

    @Test
    public void testCreateEDIEntityContentTypeWithoutEncoding() throws Exception {
        ContentType ediMessageContentType = ContentType.create(AS2MediaType.APPLICATION_EDIFACT, (String) null);
        String ediMessage = "whatever";
        ApplicationEntity applicationEntity
                = EntityUtils.createEDIEntity(ediMessage, ediMessageContentType, null, false, "sample.txt");
        String actualContentType = applicationEntity.getContentTypeValue();
        Assertions.assertEquals("application/edifact", actualContentType, "content type matches");
        Header[] actualContentDisposition = applicationEntity.getHeaders(AS2Header.CONTENT_DISPOSITION);
        Assertions.assertEquals(1, actualContentDisposition.length, "exactly one Content-Disposition header found");
        Assertions.assertEquals("Content-Disposition: attachment; filename=sample.txt",
                actualContentDisposition[0].toString());
    }

    @Test
    public void testCreateEDIEntityContentTypeWithEncoding() throws Exception {
        ContentType ediMessageContentType = ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII);
        String ediMessage = "whatever";
        ApplicationEntity applicationEntity
                = EntityUtils.createEDIEntity(ediMessage, ediMessageContentType, null, false, "sample.txt");
        String actualContentType = applicationEntity.getContentTypeValue();
        Assertions.assertEquals("application/edifact; charset=US-ASCII", actualContentType, "content type matches");
        Header[] actualContentDisposition = applicationEntity.getHeaders(AS2Header.CONTENT_DISPOSITION);
        Assertions.assertEquals(1, actualContentDisposition.length, "exactly one Content-Disposition header found");
        Assertions.assertEquals("Content-Disposition: attachment; filename=sample.txt",
                actualContentDisposition[0].toString());
    }

    @Test
    public void testCreateEDIEntityContentTypeWithoutContentDisposition() throws Exception {
        ContentType ediMessageContentType = ContentType.create(AS2MediaType.APPLICATION_EDIFACT, (String) null);
        String ediMessage = "whatever";
        ApplicationEntity applicationEntity
                = EntityUtils.createEDIEntity(ediMessage, ediMessageContentType, null, false, "");
        String actualContentType = applicationEntity.getContentTypeValue();
        Assertions.assertEquals("application/edifact", actualContentType, "content type matches");
        Header[] actualContentDisposition = applicationEntity.getHeaders(AS2Header.CONTENT_DISPOSITION);
        Assertions.assertEquals(0, actualContentDisposition.length, "no Content-Disposition headers found");
    }
}
