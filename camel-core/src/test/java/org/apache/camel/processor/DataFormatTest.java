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
package org.apache.camel.processor;

import java.io.Serializable;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * @version $Revision: 1.1 $
 */
public class DataFormatTest extends ContextTestSupport {

    public void testMarshalThenUnmarshalBean() throws Exception {
        MyBean bean = new MyBean();
        bean.name = "James";
        bean.counter = 5;

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(bean);

        template.sendBody("direct:start", bean);

        resultEndpoint.assertIsSatisfied();
    }


    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().serialization().to("direct:marshalled");
                from("direct:marshalled").unmarshal().serialization().to("mock:result");
            }
        };
    }

    protected static class MyBean implements Serializable {
        public String name;
        public int counter;

        @Override
        public boolean equals(Object o) {
            if (o instanceof MyBean) {
                MyBean that = (MyBean) o;
                return ObjectHelper.equals(this.name, that.name) && ObjectHelper.equals(this.counter,  that.counter);                
            }
            return false;
        }
    }

}