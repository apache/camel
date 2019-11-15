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
package org.apache.camel.component.aws.swf;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;

import static org.mockito.Mockito.mock;

public class CamelSWFTestSupport extends CamelTestSupport {

    protected String options =
            "accessKey=key"
                    + "&secretKey=secret"
                    + "&domainName=testDomain"
                    + "&activityList=swf-alist"
                    + "&workflowList=swf-wlist"
                    + "&version=1.0"
                    + "&eventName=testEvent"
                    + "&amazonSWClient=#amazonSWClient";

    @EndpointInject("direct:start")
    protected ProducerTemplate template;
    
    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @BindToRegistry("amazonSWClient")
    protected AmazonSimpleWorkflowClient amazonSWClient = mock(AmazonSimpleWorkflowClient.class);
}
