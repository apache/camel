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
package org.apache.camel.component.jmx;

import java.io.File;

import org.apache.camel.component.jmx.beans.ISimpleMXBean;
import org.junit.jupiter.api.Test;

public class JMXMonitorTypeStringDiffersTest extends SimpleBeanFixture {
    
    @Test
    public void differs() throws Exception {

        ISimpleMXBean simpleBean = getSimpleMXBean();
        
        simpleBean.setStringValue("changed");
        getMockFixture().waitForMessages();
        getMockFixture().assertMessageReceived(new File("src/test/resources/monitor-consumer/stringDiffers.xml"));
    }

    @Override
    protected JMXUriBuilder buildFromURI() {
        return super.buildFromURI().withMonitorType("string")
                                   .withGranularityPeriod(500)
                                   .withObservedAttribute("StringValue")
                                   .withStringToCompare("initial")
                                   .withNotifyDiffer(true);
    }
}
