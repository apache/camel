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
package org.apache.camel.dataformat.bindy;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

public abstract class CommonBindyTest extends AbstractJUnit4SpringContextTests {

    public static final String URI_MOCK_RESULT = "mock:result";
    public static final String URI_MOCK_ERROR = "mock:error";
    public static final String URI_DIRECT_START = "direct:start";
    public static final String URI_FILE_FIX = "file://src/test/data/fix?noop=true";
    public static final String URI_FILE_FIX_SIMPLE = "file://src/test/data/fix_simple?noop=true";
    public static final String URI_FILE_FIX_TAB = "file://src/test/data/fix_tab?noop=true";

    protected static final Logger LOG = LoggerFactory.getLogger(CommonBindyTest.class);

    @Produce(URI_DIRECT_START)
    public ProducerTemplate template;

    @EndpointInject(URI_MOCK_RESULT)
    public MockEndpoint result;

    @EndpointInject(URI_MOCK_ERROR)
    public MockEndpoint error;

}
