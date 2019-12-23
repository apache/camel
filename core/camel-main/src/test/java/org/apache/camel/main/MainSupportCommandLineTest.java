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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

public class MainSupportCommandLineTest {

    private class MyMainSupport extends MainCommandLineSupport {

        private CamelContext context = new DefaultCamelContext();

        @Override
        protected ProducerTemplate findOrCreateCamelTemplate() {
            return context.createProducerTemplate();
        }

        @Override
        protected CamelContext createCamelContext() {
            return null;
        }
    }

    @Test
    public void testMainSupport() throws Exception {
        MyMainSupport my = new MyMainSupport();
        my.run(new String[]{"-d", "1"});
    }

    @Test
    public void testMainSupportMaxMessages() throws Exception {
        MyMainSupport my = new MyMainSupport();
        my.run(new String[]{"-d", "1", "-dm", "2"});
    }

    @Test
    public void testMainSupportHelp() throws Exception {
        MyMainSupport my = new MyMainSupport();
        my.run(new String[]{"-h"});
    }
}
