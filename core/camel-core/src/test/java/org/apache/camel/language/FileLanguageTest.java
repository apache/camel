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
package org.apache.camel.language;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.FileUtil;
import org.junit.Test;

/**
 * Unit test for File Language.
 */
public class FileLanguageTest extends LanguageTestSupport {

    private File file;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("generator", new MyFileNameGenerator());
        return jndi;
    }

    @Override
    protected String getLanguageName() {
        return "file";
    }

    @Test
    public void testConstantExpression() throws Exception {
        assertExpression("MyBigFile.txt", "MyBigFile.txt");
    }

    @Test
    public void testMessageId() throws Exception {
        assertExpression("${id}", exchange.getIn().getMessageId());
        assertExpression("${id}.bak", exchange.getIn().getMessageId() + ".bak");
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        assertExpression("${file:onlyname}", file.getName());
        try {
            assertExpression("${file:onlyName}", file.getName());
            fail("Should have thrown exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Unknown file language syntax: onlyName at location 0"));
        }
    }

    @Test
    public void testFile() throws Exception {
        assertExpression("${file:ext}", "txt");
        assertExpression("${file:name.ext}", "txt");
        assertExpression("${file:name.ext.single}", "txt");
        assertExpression("${file:name}", "test" + File.separator + file.getName());
        assertExpression("${file:name.noext}", "test" + File.separator + "hello");
        assertExpression("${file:name.noext.single}", "test" + File.separator + "hello");
        assertExpression("${file:onlyname}", file.getName());
        assertExpression("${file:onlyname.noext}", "hello");
        assertExpression("${file:onlyname.noext.single}", "hello");
        assertExpression("${file:parent}", file.getParent());
        assertExpression("${file:path}", file.getPath());
        assertExpression("${file:absolute}", FileUtil.isAbsolute(file));
        assertExpression("${file:absolute.path}", file.getAbsolutePath());
        assertExpression("${file:length}", file.length());
        assertExpression("${file:size}", file.length());

        // modified is a long object
        Long modified = SimpleLanguage.simple("${file:modified}").evaluate(exchange, Long.class);
        assertEquals(file.lastModified(), modified.longValue());
    }

    @Test
    public void testFileUsingAlternativeStartToken() throws Exception {
        assertExpression("$simple{file:ext}", "txt");
        assertExpression("$simple{file:name.ext}", "txt");
        assertExpression("$simple{file:name}", "test" + File.separator + file.getName());
        assertExpression("$simple{file:name.noext}", "test" + File.separator + "hello");
        assertExpression("$simple{file:onlyname}", file.getName());
        assertExpression("$simple{file:onlyname.noext}", "hello");
        assertExpression("$simple{file:parent}", file.getParent());
        assertExpression("$simple{file:path}", file.getPath());
        assertExpression("$simple{file:absolute}", FileUtil.isAbsolute(file));
        assertExpression("$simple{file:absolute.path}", file.getAbsolutePath());
        assertExpression("$simple{file:length}", file.length());
        assertExpression("$simple{file:size}", file.length());

        // modified is a long object
        long modified = SimpleLanguage.simple("${file:modified}").evaluate(exchange, long.class);
        assertEquals(file.lastModified(), modified);
    }

    @Test
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

    @Test
    public void testDateUsingAlternativeStartToken() throws Exception {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertExpression("backup-$simple{date:now:yyyyMMdd}", "backup-" + now);

        String expected = new SimpleDateFormat("yyyyMMdd").format(new Date(file.lastModified()));
        assertExpression("backup-$simple{date:file:yyyyMMdd}", "backup-" + expected);

        assertExpression("backup-$simple{date:header.birthday:yyyyMMdd}", "backup-19740420");
        assertExpression("hello-$simple{date:out.header.special:yyyyMMdd}", "hello-20080808");

        try {
            this.assertExpression("nodate-$simple{date:header.xxx:yyyyMMdd}", null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSimpleAndFile() throws Exception {
        assertExpression("backup-${in.header.foo}-${file:name.noext}.bak", "backup-abc-test" + File.separator + "hello.bak");
        assertExpression("backup-${in.header.foo}-${file:onlyname.noext}.bak", "backup-abc-hello.bak");
    }

    @Test
    public void testSimpleAndFileAndBean() throws Exception {
        assertExpression("backup-${in.header.foo}-${bean:generator}-${file:name.noext}.bak", "backup-abc-generatorbybean-test" + File.separator + "hello.bak");
        assertExpression("backup-${in.header.foo}-${bean:generator}-${file:onlyname.noext}.bak", "backup-abc-generatorbybean-hello.bak");
    }

    @Test
    public void testBean() throws Exception {
        assertExpression("backup-${bean:generator}.txt", "backup-generatorbybean.txt");
        assertExpression("backup-${bean:generator.generateFilename}.txt", "backup-generatorbybean.txt");
    }

    @Test
    public void testNoEscapeAllowed() throws Exception {
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        assertExpression("target\\newdir\\onwindows\\${file:name}", "target\\newdir\\onwindows\\hello.txt");
    }

    @Test
    public void testFileNameDoubleExtension() throws Exception {
        file = new File("target/data/filelanguage/test/bigfile.tar.gz");

        String uri = "file://target/data/filelanguage?fileExist=Override";
        GenericFile<File> gf = FileConsumer.asGenericFile("target/data/filelanguage", file, null, false);

        FileEndpoint endpoint = getMandatoryEndpoint(uri, FileEndpoint.class);

        Exchange answer = endpoint.createExchange(gf);
        endpoint.configureMessage(gf, answer.getIn());

        assertEquals("bigfile.tar.gz", file.getName());
        assertExpression(answer, "${file:onlyname}", "bigfile.tar.gz");
        assertExpression(answer, "${file:ext}", "tar.gz");
    }

    @Override
    public Exchange createExchange() {
        // create the file
        String uri = "file://target/data/filelanguage?fileExist=Override";
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "test/hello.txt");

        // get the file handle
        file = new File("target/data/filelanguage/test/hello.txt");
        GenericFile<File> gf = FileConsumer.asGenericFile("target/data/filelanguage", file, null, false);

        FileEndpoint endpoint = getMandatoryEndpoint(uri, FileEndpoint.class);

        Exchange answer = endpoint.createExchange(gf);
        endpoint.configureMessage(gf, answer.getIn());

        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        answer.getIn().setHeader("birthday", cal.getTime());

        cal.set(2008, Calendar.AUGUST, 8);
        answer.getOut().setHeader("special", cal.getTime());
        return answer;
    }

    @Test
    public void testIllegalSyntax() throws Exception {
        try {
            // it should be with colon
            assertExpression("${file.name}", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Unknown function: file.name at location 0"));
        }

        try {
            assertExpression("hey ${xxx} how are you?", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Unknown function: xxx at location 4"));
        }

        try {
            assertExpression("${xxx}", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Unknown function: xxx at location 0"));
        }
    }

    @Test
    public void testConstantFilename() throws Exception {
        assertExpression("hello.txt", "hello.txt");
    }

    public class MyFileNameGenerator {
        public String generateFilename(Exchange exchange) {
            return "generatorbybean";
        }
    }
}
