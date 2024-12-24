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
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for File Language.
 */
public class FileLanguageTest extends LanguageTestSupport {
    private static final String TEST_FILE_NAME_NOEXT_1 = "hello" + UUID.randomUUID();
    private static final String TEST_FILE_NAME_1 = TEST_FILE_NAME_NOEXT_1 + ".txt";
    private static final String TEST_FILE_NAME_NOEXT_2 = "MyBigFile" + UUID.randomUUID();
    private static final String TEST_FILE_NAME_2 = TEST_FILE_NAME_NOEXT_2 + ".txt";

    private File file;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("generator", new MyFileNameGenerator());
        return jndi;
    }

    @Override
    protected String getLanguageName() {
        return "file";
    }

    @Test
    public void testConstantExpression() {
        assertExpression(TEST_FILE_NAME_2, TEST_FILE_NAME_2);
    }

    @Test
    public void testMessageId() {
        assertExpression("${id}", exchange.getIn().getMessageId());
        assertExpression("${id}.bak", exchange.getIn().getMessageId() + ".bak");
    }

    @Test
    public void testInvalidSyntax() {
        assertExpression("${file:onlyname}", file.getName());
        ExpressionIllegalSyntaxException e = assertThrows(ExpressionIllegalSyntaxException.class,
                () -> assertExpression("${file:onlyName}", file.getName()),
                "Should have thrown exception");

        assertTrue(e.getMessage().startsWith("Unknown file language syntax: onlyName at location 0"));
    }

    @Test
    public void testFile() {
        assertExpression("${file:ext}", "txt");
        assertExpression("${file:name.ext}", "txt");
        assertExpression("${file:name.ext.single}", "txt");
        assertExpression("${file:name}", "test" + File.separator + file.getName());
        assertExpression("${file:name.noext}", "test" + File.separator + TEST_FILE_NAME_NOEXT_1);
        assertExpression("${file:name.noext.single}", "test" + File.separator + TEST_FILE_NAME_NOEXT_1);
        assertExpression("${file:onlyname}", file.getName());
        assertExpression("${file:onlyname.noext}", TEST_FILE_NAME_NOEXT_1);
        assertExpression("${file:onlyname.noext.single}", TEST_FILE_NAME_NOEXT_1);
        assertExpression("${file:parent}", file.getParent());
        assertExpression("${file:path}", file.getPath());
        assertExpression("${file:absolute}", FileUtil.isAbsolute(file));
        assertExpression("${file:absolute.path}", file.getAbsolutePath());
        assertExpression("${file:length}", file.length());
        assertExpression("${file:size}", file.length());

        // modified is a long object
        Long modified = context.resolveLanguage("simple").createExpression("${file:modified}").evaluate(exchange, Long.class);
        assertEquals(file.lastModified(), modified.longValue());
    }

    @Test
    public void testFileUsingAlternativeStartToken() {
        assertExpression("$simple{file:ext}", "txt");
        assertExpression("$simple{file:name.ext}", "txt");
        assertExpression("$simple{file:name}", "test" + File.separator + file.getName());
        assertExpression("$simple{file:name.noext}", "test" + File.separator + TEST_FILE_NAME_NOEXT_1);
        assertExpression("$simple{file:onlyname}", file.getName());
        assertExpression("$simple{file:onlyname.noext}", TEST_FILE_NAME_NOEXT_1);
        assertExpression("$simple{file:parent}", file.getParent());
        assertExpression("$simple{file:path}", file.getPath());
        assertExpression("$simple{file:absolute}", FileUtil.isAbsolute(file));
        assertExpression("$simple{file:absolute.path}", file.getAbsolutePath());
        assertExpression("$simple{file:length}", file.length());
        assertExpression("$simple{file:size}", file.length());

        // modified is a long object
        long modified = context.resolveLanguage("simple").createExpression("${file:modified}").evaluate(exchange, long.class);
        assertEquals(file.lastModified(), modified);
    }

    @Test
    public void testDate() {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertExpression("backup-${date:now:yyyyMMdd}", "backup-" + now);

        String expected = new SimpleDateFormat("yyyyMMdd").format(new Date(file.lastModified()));
        assertExpression("backup-${date:file:yyyyMMdd}", "backup-" + expected);

        assertExpression("backup-${date:header.birthday:yyyyMMdd}", "backup-19740420");
        assertExpression(TEST_FILE_NAME_NOEXT_1 + "-${date:header.special:yyyyMMdd}", TEST_FILE_NAME_NOEXT_1 + "-20080808");

        assertThrows(IllegalArgumentException.class,
                () -> this.assertExpression("nodate-${date:header.xxx:yyyyMMdd}", null),
                "Should have thrown IllegalArgumentException");
    }

    @Test
    public void testDateUsingAlternativeStartToken() {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertExpression("backup-$simple{date:now:yyyyMMdd}", "backup-" + now);

        String expected = new SimpleDateFormat("yyyyMMdd").format(new Date(file.lastModified()));
        assertExpression("backup-$simple{date:file:yyyyMMdd}", "backup-" + expected);

        assertExpression("backup-$simple{date:header.birthday:yyyyMMdd}", "backup-19740420");
        assertExpression(TEST_FILE_NAME_NOEXT_1 + "-$simple{date:header.special:yyyyMMdd}",
                TEST_FILE_NAME_NOEXT_1 + "-20080808");

        assertThrows(IllegalArgumentException.class,
                () -> this.assertExpression("nodate-$simple{date:header.xxx:yyyyMMdd}", null),
                "Should have thrown IllegalArgumentException");
    }

    @Test
    public void testSimpleAndFile() {
        assertExpression("backup-${in.header.foo}-${file:name.noext}.bak",
                "backup-abc-test" + File.separator + TEST_FILE_NAME_NOEXT_1 + ".bak");
        assertExpression("backup-${in.header.foo}-${file:onlyname.noext}.bak", "backup-abc-" + TEST_FILE_NAME_NOEXT_1 + ".bak");
    }

    @Test
    public void testSimpleAndFileAndBean() {
        assertExpression("backup-${in.header.foo}-${bean:generator}-${file:name.noext}.bak",
                "backup-abc-generatorbybean-test" + File.separator + TEST_FILE_NAME_NOEXT_1 + ".bak");
        assertExpression("backup-${in.header.foo}-${bean:generator}-${file:onlyname.noext}.bak",
                "backup-abc-generatorbybean-" + TEST_FILE_NAME_NOEXT_1 + ".bak");
    }

    @Test
    public void testBean() {
        assertExpression("backup-${bean:generator}.txt", "backup-generatorbybean.txt");
        assertExpression("backup-${bean:generator.generateFilename}.txt", "backup-generatorbybean.txt");
    }

    @Test
    public void testNoEscapeAllowed() {
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_1);
        assertExpression("target\\newdir\\onwindows\\${file:name}", "target\\newdir\\onwindows\\" + TEST_FILE_NAME_1);
    }

    @Test
    public void testFileNameDoubleExtension() {
        file = testFile("test/" + TEST_FILE_NAME_NOEXT_2 + ".tar.gz").toFile();

        String uri = fileUri("?fileExist=Override");
        GenericFile<File> gf = FileConsumer.asGenericFile(testDirectory().toString(), file, null, false);

        FileEndpoint endpoint = getMandatoryEndpoint(uri, FileEndpoint.class);

        Exchange answer = endpoint.createExchange(gf);
        endpoint.configureMessage(gf, answer.getIn());

        assertEquals(TEST_FILE_NAME_NOEXT_2 + ".tar.gz", file.getName());
        assertExpression(answer, "${file:onlyname}", TEST_FILE_NAME_NOEXT_2 + ".tar.gz");
        assertExpression(answer, "${file:ext}", "tar.gz");
    }

    @Override
    public Exchange createExchange() {
        // create the file
        String uri = "file://" + testDirectory().toString() + "?fileExist=Override";
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "test/" + TEST_FILE_NAME_1);

        // get the file handle
        file = testDirectory().resolve("test/" + TEST_FILE_NAME_1).toFile();
        GenericFile<File> gf = FileConsumer.asGenericFile(testDirectory().toString(), file, null, false);

        FileEndpoint endpoint = getMandatoryEndpoint(uri, FileEndpoint.class);

        Exchange answer = endpoint.createExchange(gf);
        endpoint.configureMessage(gf, answer.getIn());

        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        answer.getMessage().setHeader("birthday", cal.getTime());

        cal.set(2008, Calendar.AUGUST, 8);
        answer.getMessage().setHeader("special", cal.getTime());
        return answer;
    }

    @Test
    public void testIllegalSyntax() {
        ExpressionIllegalSyntaxException e1 = assertThrows(ExpressionIllegalSyntaxException.class,
                () -> assertExpression("${file.name}", ""),
                "Should have thrown an exception");

        assertTrue(e1.getMessage().startsWith("Unknown function: file.name at location 0"));

        ExpressionIllegalSyntaxException e2 = assertThrows(ExpressionIllegalSyntaxException.class,
                () -> assertExpression("hey ${xxx} how are you?", ""),
                "Should have thrown an exception");

        assertTrue(e2.getMessage().startsWith("Unknown function: xxx at location 4"));

        ExpressionIllegalSyntaxException e3 = assertThrows(ExpressionIllegalSyntaxException.class,
                () -> assertExpression("${xxx}", ""),
                "Should have thrown an exception");

        assertTrue(e3.getMessage().startsWith("Unknown function: xxx at location 0"));
    }

    @Test
    public void testConstantFilename() {
        assertExpression(TEST_FILE_NAME_1, TEST_FILE_NAME_1);
    }

    public static class MyFileNameGenerator {
        public String generateFilename(Exchange exchange) {
            return "generatorbybean";
        }
    }
}
