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
package org.apache.camel.converter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class PropertyEditorTypeConverterIssueTest extends ContextTestSupport {

    @Test
    public void testPropertyEditorTypeConverter() throws Exception {
        // test that converters a custom object (MyBean) to a String which
        // causes
        // PropertyEditorTypeConverter to be used. And this test times how fast
        // this is. As we want to optimize PropertyEditorTypeConverter to be
        // faster
        MyBean bean = new MyBean();
        bean.setBar("Hello");

        StopWatch watch = new StopWatch();
        for (int i = 0; i < 500; i++) {
            String s = context.getTypeConverter().convertTo(String.class, bean);
            log.debug(s);
            assertNotNull(s);
        }
        log.info("Time taken: " + watch.taken());
    }
}
