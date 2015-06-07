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
package org.apache.camel.component.quartz2;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.URISupport;
import org.junit.Test;
import org.quartz.JobDetail;

public class DelegateEndpointQuartzTest extends CamelTestSupport {
    
    @Test
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);
        
        assertMockEndpointsSatisfied();

        JobDetail job = mock.getReceivedExchanges().get(0).getIn().getHeader("jobDetail", JobDetail.class);
        assertNotNull(job);

        assertEquals("cron", job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE));
        assertEquals("0/2 * * * * ?", job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("my:quartz2://myGroup/myTimerName?cron=0/2+*+*+*+*+?").to("mock:result");
            }
        };
    }
    
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry =  new JndiRegistry(createJndiContext());
        registry.bind("my", new MyComponent());
        return registry;
    }
    
    class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
            
            String childUri = remaining;
            // we need to apply the params here
            if (parameters != null && parameters.size() > 0) {
                childUri = childUri + "?" + URISupport.createQueryString(parameters);
            }
            // need to clean the parameters to avoid default component verify parameter complain
            parameters.clear();
            Endpoint childEndpoint = context.getEndpoint(childUri);
            return new MyEndpoint(uri, childEndpoint);
        }
        
    }
    
    class MyEndpoint extends DefaultEndpoint implements DelegateEndpoint {
        private final Endpoint childEndpoint;
        
        MyEndpoint(String uri, Endpoint childEndpoint) {
            super(uri);
            this.childEndpoint = childEndpoint;
        }

        @Override
        public Producer createProducer() throws Exception {
            return childEndpoint.createProducer();
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return childEndpoint.createConsumer(processor);
        }

        @Override
        public boolean isSingleton() {
            return false;
        }

        @Override
        public Endpoint getEndpoint() {
            return childEndpoint;
        }

        @Override
        protected String createEndpointUri() {
            return "my:" + childEndpoint.getEndpointUri();
        }

    }

}
