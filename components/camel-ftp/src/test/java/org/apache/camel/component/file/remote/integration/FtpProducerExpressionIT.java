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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.camel.BindToRegistry;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;

/**
 * Unit test for FTP using expression (file language)
 */
public class FtpProducerExpressionIT extends FtpServerTestSupport {

    @BindToRegistry("myguidgenerator")
    private final MyGuidGenerator guid = new MyGuidGenerator();

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/filelanguage?password=admin";
    }

    @Test
    public void testProduceBeanByExpression() {
        template.sendBody(getFtpUrl() + "&fileName=${bean:myguidgenerator}.bak", "Hello World");

        assertFileExists(service.ftpFile("filelanguage/123.bak"));
    }

    @Test
    public void testProduceBeanByHeader() {
        sendFile(getFtpUrl(), "Hello World", "${bean:myguidgenerator}.bak");

        assertFileExists(service.ftpFile("filelanguage/123.bak"));
    }

    @Test
    public void testProducerDateByHeader() {
        sendFile(getFtpUrl(), "Hello World", "myfile-${date:now:yyyyMMdd}.txt");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(service.ftpFile("filelanguage/myfile-") + date + ".txt");
    }

    @Test
    public void testProducerDateByExpression() {
        template.sendBody(getFtpUrl() + "&fileName=myfile-${date:now:yyyyMMdd}.txt", "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(service.ftpFile("filelanguage/myfile-") + date + ".txt");
    }

    @Test
    public void testProducerComplexByExpression() {
        // need one extra subdirectory (=foo) to be able to start with .. in the
        // fileName option
        String url = "ftp://admin@localhost:{{ftp.server.port}}/filelanguage/foo?password=admin&jailStartingDirectory=false";

        String expression = "../filelanguageinbox/myfile-${bean:myguidgenerator.guid}-${date:now:yyyyMMdd}.txt";
        template.sendBody(url + "&fileName=" + expression, "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(service.ftpFile("filelanguage/filelanguageinbox/myfile-123-") + date + ".txt");
    }

    @Test
    public void testProducerSimpleWithHeaderByExpression() {
        template.sendBodyAndHeader(getFtpUrl() + "&fileName=myfile-${header.foo}.txt", "Hello World", "foo", "abc");

        assertFileExists(service.ftpFile("filelanguage/myfile-abc.txt"));
    }

    @Test
    public void testProducerWithDateHeader() {
        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        Date date = cal.getTime();

        template.sendBodyAndHeader(getFtpUrl() + "&fileName=mybirthday-${date:header.birthday:yyyyMMdd}.txt", "Hello World",
                "birthday", date);

        assertFileExists(service.ftpFile("filelanguage/mybirthday-19740420.txt"));
    }

    public static class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}
