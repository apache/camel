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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.camel.impl.JndiRegistry;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for FTP using expression (file language)
 */
public class FtpProducerExpressionTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/filelanguage?password=admin";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/filelanguage");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testProduceBeanByExpression() throws Exception {
        template.sendBody(getFtpUrl() + "&fileName=${bean:myguidgenerator}.bak", "Hello World");

        assertFileExists(FTP_ROOT_DIR + "/filelanguage/123.bak");
    }

    @Test
    public void testProduceBeanByHeader() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "${bean:myguidgenerator}.bak");

        assertFileExists(FTP_ROOT_DIR + "/filelanguage/123.bak");
    }

    @Test
    public void testProducerDateByHeader() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "myfile-${date:now:yyyyMMdd}.txt");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(FTP_ROOT_DIR + "/filelanguage/myfile-" + date + ".txt");
    }

    @Test
    public void testProducerDateByExpression() throws Exception {
        template.sendBody(getFtpUrl() + "&fileName=myfile-${date:now:yyyyMMdd}.txt", "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(FTP_ROOT_DIR + "/filelanguage/myfile-" + date + ".txt");
    }

    @Test
    public void testProducerComplexByExpression() throws Exception {
        // need one extra subdirectory (=foo) to be able to start with .. in the fileName option
        String url = "ftp://admin@localhost:" + getPort() + "/filelanguage/foo?password=admin";
        
        String expression = "../filelanguageinbox/myfile-${bean:myguidgenerator.guid}-${date:now:yyyyMMdd}.txt";
        template.sendBody(url + "&fileName=" + expression, "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(FTP_ROOT_DIR + "/filelanguage/filelanguageinbox/myfile-123-" + date + ".txt");
    }

    @Test
    public void testProducerSimpleWithHeaderByExpression() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&fileName=myfile-${in.header.foo}.txt",
                "Hello World", "foo", "abc");

        assertFileExists(FTP_ROOT_DIR + "/filelanguage/myfile-abc.txt");
    }

    @Test
    public void testProducerWithDateHeader() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        Date date = cal.getTime();

        template.sendBodyAndHeader(getFtpUrl() + "&fileName=mybirthday-${date:in.header.birthday:yyyyMMdd}.txt",
                "Hello World", "birthday", date);

        assertFileExists(FTP_ROOT_DIR + "/filelanguage/mybirthday-19740420.txt");
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}