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
package org.apache.camel.component.jmx;

import java.net.URI;

import org.apache.camel.Message;
import org.apache.camel.component.jmx.beans.ISimpleMXBean;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;


/**
 * Tests that we get the handback object in the message header
 */
public class JMXHandbackTest extends SimpleBeanFixture {

    URI hb;

    @Before
    public void setUp() throws Exception {
        hb = new URI("urn:some:handback:object");
        super.setUp();
    }

    @Test
    public void test() throws Exception {
        ISimpleMXBean simpleBean = getSimpleMXBean();
        simpleBean.userData("myUserData");

        getMockFixture().waitForMessages();

        Message m = getMockFixture().getMessage(0);
        URI uri = (URI) m.getHeader("jmx.handback");
        assertSame(hb, uri);
    }

    @Override
    protected JMXUriBuilder buildFromURI() {
        return super.buildFromURI().withHandback("#hb").withFormat("raw");
    }

    @Override
    protected void initRegistry() {
        super.initRegistry();
        getRegistry().put("hb", hb);
    }
}
