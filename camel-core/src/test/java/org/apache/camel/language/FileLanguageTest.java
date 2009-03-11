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
package org.apache.camel.language;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.language.simple.FileLanguage;

/**
 * Unit test for File Language.
 */
public class FileLanguageTest extends LanguageTestSupport {

    private File file;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("generator", new MyFileNameGenerator());
        return jndi;
    }

    protected String getLanguageName() {
        return "file";
    }

    public void testConstantExpression() throws Exception {
        assertExpression("MyBigFile.txt", "MyBigFile.txt");
    }

    public void testMessageId() throws Exception {
        assertExpression("${id}", exchange.getIn().getMessageId());
        assertExpression("${id}.bak", exchange.getIn().getMessageId() + ".bak");
    }

    public void testFile() throws Exception {
        assertExpression("${file:ext}", "txt");
        assertExpression("${file:name}", "test" + File.separator + file.getName());
        assertExpression("${file:name.noext}", "test" + File.separator + "hello");
        assertExpression("${file:onlyname}", file.getName());
        assertExpression("${file:onlyname.noext}", "hello");
        assertExpression("${file:parent}", file.getParent());
        assertExpression("${file:path}", file.getPath());
        assertExpression("${file:absolute}", file.isAbsolute());
        assertExpression("${file:absolute.path}", file.getAbsolutePath());
        assertExpression("${file:length}", file.length());

        // modified is a Date object
        Date modified = FileLanguage.file("file:modified").evaluate(exchange, Date.class);
        assertEquals(new Date(file.lastModified()), modified);
    }

    public void testDate() throws Exception {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertExpression("backup-${date:now:yyyyMMdd}", "backup-" + now);

        String expected = new SimpleDateFormat("yyyyMMdd").format(new Date(file.lastModified()));
        assertExpression("backup-${date:file:yyyyMMdd}", "backup-" + expected);

        assertExpression("backup-${date:header.birthday:yyyyMMdd}", "backup-19740420");
        assertExpression("hello-${date:out.header.special:yyyyMMdd}", "hello-20080808");

        try {
            this.assertExpression("nodate-${date:header.xxx:yyyyMMdd}", null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testSimpleAndFile() throws Exception {
        assertExpression("backup-${in.header.foo}-${file:name.noext}.bak", "backup-abc-test" + File.separator + "hello.bak");
        assertExpression("backup-${in.header.foo}-${file:onlyname.noext}.bak", "backup-abc-hello.bak");
    }

    public void testSimpleAndFileAndBean() throws Exception {
        assertExpression("backup-${in.header.foo}-${bean:generator}-${file:name.noext}.bak", "backup-abc-generatorbybean-test" + File.separator + "hello.bak");
        assertExpression("backup-${in.header.foo}-${bean:generator}-${file:onlyname.noext}.bak", "backup-abc-generatorbybean-hello.bak");
    }

    public void testBean() throws Exception {
        assertExpression("backup-${bean:generator}.txt", "backup-generatorbybean.txt");
        assertExpression("backup-${bean:generator.generateFilename}.txt", "backup-generatorbybean.txt");
    }

    public Exchange createExchange() {
        // create the file
        String uri = "file://target/filelanguage";
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "test/hello.txt");

        // get the file handle
        file = new File("target/filelanguage/test/hello.txt");
        GenericFile<File> gf = FileConsumer.asGenericFile("target/filelanguage", file);

        FileEndpoint endpoint = getMandatoryEndpoint(uri, FileEndpoint.class);

        GenericFileExchange<File> answer = endpoint.createExchange(gf);
        endpoint.configureMessage(gf, answer.getIn());

        Calendar cal = GregorianCalendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        answer.getIn().setHeader("birthday", cal.getTime());

        cal.set(2008, Calendar.AUGUST, 8);
        answer.getOut().setHeader("special", cal.getTime());
        return answer;
    }

    public class MyFileNameGenerator {
        public String generateFilename(Exchange exchange) {
            return "generatorbybean";
        }
    }
}
