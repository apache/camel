/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.bam;

import static org.apache.camel.language.juel.JuelExpression.el;

/**
 * @version $Revision: 1.1 $
 */
public class MultipleProcessesTest extends BamRouteTest {

    @Override
    public void testBam() throws Exception {
        overdueEndpoint.expectedMessageCount(1);
        overdueEndpoint.message(0).predicate(el("${in.body.correlationKey == '124'}"));

        sendAMessages();
        sendBMessages();

        //overdueEndpoint.assertIsSatisfied();
    }

    protected void sendAMessages() {
        template.sendBody("direct:a", "<hello id='123'>A</hello>");
        template.sendBody("direct:a", "<hello id='124'>B</hello>");
        template.sendBody("direct:a", "<hello id='125'>C</hello>");
    }

    protected void sendBMessages() {
        template.sendBody("direct:b", "<hello id='123'>A</hello>");
        template.sendBody("direct:b", "<hello id='125'>C</hello>");
    }
}
