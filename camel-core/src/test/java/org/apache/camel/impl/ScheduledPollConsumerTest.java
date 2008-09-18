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

package org.apache.camel.impl;

import junit.framework.Assert;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

public class ScheduledPollConsumerTest extends ContextTestSupport {
    
    public void testExceptionOnPollGetsThrownOnShutdown() throws Exception {
        Exception expectedException = new Exception("Hello, I should be thrown on shutdown only!");
        Exception actualException = null;
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(expectedException);

        consumer.start();
        // exception is caught and saved
        consumer.run(); 
        
        try {
            // exception should be thrown
            consumer.stop();           
        } catch (Exception e) {
            actualException = e;
        }
        
        // make sure its the right exception!
        Assert.assertEquals(expectedException, actualException);
    }
    
    public void testNoExceptionOnPollAndNoneThrownOnShutdown() throws Exception {
        Exception actualException = null;
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(null);

        consumer.start();
        consumer.run(); 
        
        try {
            // exception should not be thrown
            consumer.stop();           
        } catch (Exception e) {
            actualException = e;
        }
        
        // make sure no exception was thrown
        Assert.assertEquals(null, actualException);
    }
}
