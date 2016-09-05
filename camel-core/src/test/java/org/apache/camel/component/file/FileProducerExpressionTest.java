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
package org.apache.camel.component.file;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.JndiRegistry;

import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * Unit test for expression option for file producer.
 */
public class FileProducerExpressionTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/filelanguage");
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }

    public void testProducerFileNameHeaderNotEvaluated() {
        if (!isPlatform("windows")) {
            template.sendBodyAndHeader("file://target/filelanguage", "Hello World", Exchange.FILE_NAME, "$simple{myfile-${id}}.txt");
            assertFileExists("target/filelanguage/$simple{myfile-${id}}.txt");
        }
    }

    public void testProduceBeanByExpression() throws Exception {
        template.sendBody("file://target/filelanguage?fileName=${bean:myguidgenerator}.bak", "Hello World");

        assertFileExists("target/filelanguage/123.bak");
    }

    public void testProducerDateByHeader() throws Exception {
        template.sendBodyAndHeader("file://target/filelanguage", "Hello World",
            Exchange.FILE_NAME, simple("myfile-${date:now:yyyyMMdd}.txt"));

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/filelanguage/myfile-" + date + ".txt");
    }

    public void testProducerDateByExpression() throws Exception {
        template.sendBody("file://target/filelanguage?fileName=myfile-${date:now:yyyyMMdd}.txt", "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/filelanguage/myfile-" + date + ".txt");
    }

    public void testProducerComplexByExpression() throws Exception {
        String expression = "../filelanguageinbox/myfile-${bean:myguidgenerator.guid}-${date:now:yyyyMMdd}.txt";
        template.sendBody("file://target/filelanguage?fileName=" + expression, "Hello World");

        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/filelanguageinbox/myfile-123-" + date + ".txt");
    }

    public void testProducerSimpleWithHeaderByExpression() throws Exception {
        template.sendBodyAndHeader("file://target/filelanguage?fileName=myfile-${in.header.foo}.txt",
            "Hello World", "foo", "abc");

        assertFileExists("target/filelanguage/myfile-abc.txt");
    }

    public void testProducerWithDateHeader() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        Date date = cal.getTime();

        template.sendBodyAndHeader("file://target/filelanguage?fileName=mybirthday-${date:in.header.birthday:yyyyMMdd}.txt",
            "Hello World", "birthday", date);

        assertFileExists("target/filelanguage/mybirthday-19740420.txt");
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}
