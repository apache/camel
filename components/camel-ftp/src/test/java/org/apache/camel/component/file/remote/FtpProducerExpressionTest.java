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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for FTP using expression (file language)
 */
public class FtpProducerExpressionTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/filelanguage?password=admin";
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory(FTP_ROOT_DIR + "filelanguage");
        deleteDirectory("target/filelanguage");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }

    public void testProduceBeanByExpression() throws Exception {
        template.sendBody(getFtpUrl() + "&fileExpression=${bean:myguidgenerator}.bak", "Hello World");

        assertFileExists(FTP_ROOT_DIR + "filelanguage/123.bak");
    }

    public void testProduceBeanByHeader() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "${bean:myguidgenerator}.bak");

        assertFileExists(FTP_ROOT_DIR + "filelanguage/123.bak");
    }

    public void testProducerDateByHeader() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "myfile-${date:now:yyyyMMdd}.txt");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(FTP_ROOT_DIR + "filelanguage/myfile-" + date + ".txt");
    }

    public void testProducerDateByExpression() throws Exception {
        template.sendBody(getFtpUrl() + "&fileExpression=myfile-${date:now:yyyyMMdd}.txt", "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(FTP_ROOT_DIR + "filelanguage/myfile-" + date + ".txt");
    }

    public void testProducerComplexByExpression() throws Exception {
        String expression = "../filelanguageinbox/myfile-${bean:myguidgenerator.guid}-${date:now:yyyyMMdd}.txt";
        template.sendBody(getFtpUrl() + "&fileExpression=" + expression, "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(FTP_ROOT_DIR + "filelanguageinbox/myfile-123-" + date + ".txt");
    }

    public void testProducerSimpleWithHeaderByExpression() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&fileExpression=myfile-${in.header.foo}.txt",
                "Hello World", "foo", "abc");

        assertFileExists(FTP_ROOT_DIR + "filelanguage/myfile-abc.txt");
    }

    public void testProducerWithDateHeader() throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        Date date = cal.getTime();

        template.sendBodyAndHeader(getFtpUrl() + "&fileExpression=mybirthday-${date:in.header.birthday:yyyyMMdd}.txt",
                "Hello World", "birthday", date);

        assertFileExists(FTP_ROOT_DIR + "filelanguage/mybirthday-19740420.txt");
    }

    private static void assertFileExists(String filename) {
        File file = new File(filename);
        file = file.getAbsoluteFile();
        assertTrue("File " + filename + " should exists", file.exists());
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }

}