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
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.component.file.FileExchange;
import org.apache.camel.impl.JndiRegistry;

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

    public void testMessageId() throws Exception {
        assertExpression("${id}", exchange.getIn().getMessageId());
        assertExpression("${id}.bak", exchange.getIn().getMessageId() + ".bak");
    }

    public void testFile() throws Exception {
        assertExpression("${file:name}", file.getName());
        assertExpression("${file:name.noext}", "hello");
        assertExpression("${file:parent}", file.getParent());
        assertExpression("${file:path}", file.getPath());
        assertExpression("${file:absolute}", file.getAbsolutePath());
        assertExpression("${file:canonical.path}", file.getCanonicalPath());
    }

    public void testDate() throws Exception {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertExpression("backup-${date:now:yyyyMMdd}", "backup-" + now);

        String expected = new SimpleDateFormat("yyyyMMdd").format(new Date(file.lastModified()));
        assertExpression("backup-${date:file:yyyyMMdd}", "backup-" + expected);
    }

    public void testSimple() throws Exception {
        assertExpression("${body}", "<hello id='m123'>world!</hello>");
        assertExpression("${in.header.foo}", "abc");

        // end users might add simple: prefix so we support that too
        assertExpression("${simple:in.header.foo}", "abc");
        assertExpression("${simple:body}", "<hello id='m123'>world!</hello>");
    }

    public void testSimpleAndFile() throws Exception {
        assertExpression("backup-${in.header.foo}-${file:name.noext}.bak", "backup-abc-hello.bak");
    }

    public void testSimpleAndFileAndBean() throws Exception {
        assertExpression("backup-${in.header.foo}-${bean:generator}-${file:name.noext}.bak", "backup-abc-generatorbybean-hello.bak");
    }

    public void testBean() throws Exception {
        assertExpression("backup-${bean:generator}.txt", "backup-generatorbybean.txt");
        assertExpression("backup-${bean:generator.generateFilename}.txt", "backup-generatorbybean.txt");
    }

    public Exchange createExchange() {
        file = new File("target/filelanguage/hello.txt");
        Exchange answer = new FileExchange(context, ExchangePattern.InOnly, file);
        return answer;
    }

    public class MyFileNameGenerator {
        public String generateFilename(FileExchange exchange) {
            return "generatorbybean";
        }
    }
}
