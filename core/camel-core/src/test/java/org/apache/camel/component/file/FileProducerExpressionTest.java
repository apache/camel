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
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * Unit test for expression option for file producer.
 */
public class FileProducerExpressionTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/filelanguage");
        super.setUp();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }

    @Test
    public void testProducerFileNameHeaderNotEvaluated() {
        if (!isPlatform("windows")) {
            template.sendBodyAndHeader("file://target/data/filelanguage", "Hello World", Exchange.FILE_NAME, "$simple{myfile-${id}}.txt");
            assertFileExists("target/data/filelanguage/$simple{myfile-${id}}.txt");
        }
    }

    @Test
    public void testProduceBeanByExpression() throws Exception {
        template.sendBody("file://target/data/filelanguage?fileName=${bean:myguidgenerator}.bak", "Hello World");

        assertFileExists("target/data/filelanguage/123.bak");
    }

    @Test
    public void testProducerDateByHeader() throws Exception {
        template.sendBodyAndHeader("file://target/data/filelanguage", "Hello World", Exchange.FILE_NAME, simple("myfile-${date:now:yyyyMMdd}.txt"));

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/data/filelanguage/myfile-" + date + ".txt");
    }

    @Test
    public void testProducerDateByExpression() throws Exception {
        template.sendBody("file://target/data/filelanguage?fileName=myfile-${date:now:yyyyMMdd}.txt", "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/data/filelanguage/myfile-" + date + ".txt");
    }

    @Test
    public void testProducerComplexByExpression() throws Exception {
        String expression = "../filelanguageinbox/myfile-${bean:myguidgenerator.guid}-${date:now:yyyyMMdd}.txt";
        template.sendBody("file://target/data/filelanguage?jailStartingDirectory=false&fileName=" + expression, "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/data/filelanguageinbox/myfile-123-" + date + ".txt");
    }

    @Test
    public void testProducerSimpleWithHeaderByExpression() throws Exception {
        template.sendBodyAndHeader("file://target/data/filelanguage?fileName=myfile-${in.header.foo}.txt", "Hello World", "foo", "abc");

        assertFileExists("target/data/filelanguage/myfile-abc.txt");
    }

    @Test
    public void testProducerWithDateHeader() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        Date date = cal.getTime();

        template.sendBodyAndHeader("file://target/data/filelanguage?fileName=mybirthday-${date:in.header.birthday:yyyyMMdd}.txt", "Hello World", "birthday", date);

        assertFileExists("target/data/filelanguage/mybirthday-19740420.txt");
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}
