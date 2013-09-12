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
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

/**
 * @version
 */
public class FileProducerConsumedFileNameEvaluationTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/producerconsumedfilename");
        super.setUp();
    }

    public void testFileNameNotEvaluatedWhenMatchingConsumed() throws Exception {
        Map<String, Object> headers = new TreeMap<String, Object>();
        headers.put(Exchange.FILE_NAME, "file-${date:now:yyyyMMdd}");
        headers.put(Exchange.FILE_NAME_CONSUMED, "file-${date:now:yyyyMMdd}");
        template.sendBodyAndHeaders("file://target/producerconsumedfilename", "Hello World", headers);
        assertFileExists("target/producerconsumedfilename/file-${date:now:yyyyMMdd}");
    }

    public void testFileNameEvaluatedWhenNotMatchingConsumed() throws Exception {
        Map<String, Object> headers = new TreeMap<String, Object>();
        headers.put(Exchange.FILE_NAME, "file-${date:now:yyyyMMdd}.txt");
        headers.put(Exchange.FILE_NAME_CONSUMED, "file-consumed");
        template.sendBodyAndHeaders("file://target/producerconsumedfilename", "Hello World", headers);
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/producerconsumedfilename/file-" + date + ".txt");
    }

    public void testFileNameEvaluatedWhenConsumedNull() throws Exception {
        Map<String, Object> headers = new TreeMap<String, Object>();
        headers.put(Exchange.FILE_NAME, "file-${date:now:yyyyMMdd}.txt");
        template.sendBodyAndHeaders("file://target/producerconsumedfilename", "Hello World", headers);
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertFileExists("target/producerconsumedfilename/file-" + date + ".txt");
    }
}
