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
package org.apache.camel.component.file;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Unit test for expression option for file producer.
 */
public class FileProducerExpressionTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void testProducerFileNameHeaderNotEvaluated() {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME,
                "$simple{myfile-${id}}.txt");
        assertFileExists(testFile("$simple{myfile-${id}}.txt"));
    }

    @Test
    public void testProduceBeanByExpression() throws Exception {
        template.sendBody(fileUri("?fileName=${bean:myguidgenerator}.bak"), "Hello World");

        assertFileExists(testFile("123.bak"));
    }

    @Test
    public void testProducerDateByHeader() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME,
                context.resolveLanguage("simple").createExpression("myfile-${date:now:yyyyMMdd}.txt"));

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(testFile("myfile-" + date + ".txt"));
    }

    @Test
    public void testProducerDateByExpression() throws Exception {
        template.sendBody(fileUri("?fileName=myfile-${date:now:yyyyMMdd}.txt"), "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(testFile("myfile-" + date + ".txt"));
    }

    @Test
    public void testProducerComplexByExpression() throws Exception {
        String expression = "../filelanguageinbox/myfile-${bean:myguidgenerator.guid}-${date:now:yyyyMMdd}.txt";
        template.sendBody(fileUri("?jailStartingDirectory=false&fileName=" + expression), "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists(testFile("../filelanguageinbox/myfile-123-" + date + ".txt"));
    }

    @Test
    public void testProducerSimpleWithHeaderByExpression() throws Exception {
        template.sendBodyAndHeader(fileUri("?fileName=myfile-${in.header.foo}.txt"), "Hello World", "foo",
                "abc");

        assertFileExists(testFile("myfile-abc.txt"));
    }

    @Test
    public void testProducerWithDateHeader() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        Date date = cal.getTime();

        template.sendBodyAndHeader(fileUri("?fileName=mybirthday-${date:header.birthday:yyyyMMdd}.txt"),
                "Hello World", "birthday", date);

        assertFileExists(testFile("mybirthday-19740420.txt"));
    }

    public static class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}
