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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Handler;
import org.apache.camel.language.xpath.XPath;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the XPath annotation 'header' value which when set will cause the XPath to be evaluated on the required header,
 * otherwise it will be applied to the body
 */
public class BeanWithXPathInjectionUsingHeaderValueTest extends ContextTestSupport {
    protected MyBean myBean = new MyBean();

    @Test
    public void testConstantXPathHeaders() throws Exception {
        template.sendBodyAndHeader("bean:myBean", "<response>OK</response>", "invoiceDetails",
                "<invoice><person><name>Alan</name><date>26/08/2012</date></person></invoice>");

        assertEquals("OK", myBean.response, "bean response:  " + myBean);
        assertEquals("Alan", myBean.userName, "bean userName: " + myBean);
        assertEquals("26/08/2012", myBean.date, "bean date:  " + myBean);

        template.sendBodyAndHeader("bean:myBean", "<response>OK</response>", "invoiceDetails",
                "<invoice><person><name>Jack</name><date>27/08/2012</date></person></invoice>");

        assertEquals("OK", myBean.response, "bean response:  " + myBean);
        assertEquals("Jack", myBean.userName, "bean userName: " + myBean);
        assertEquals("27/08/2012", myBean.date, "bean date:  " + myBean);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", myBean);
        return answer;
    }

    public static class MyBean {
        public String userName;
        public String date;
        public String response;

        @Handler
        public void handler(
                @XPath("//response/text()") String response,
                @XPath(source = "header:invoiceDetails", value = "//invoice/person/name/text()") String userName,
                @XPath(source = "header:invoiceDetails", value = "//invoice/person/date",
                       resultType = String.class) String date) {
            this.response = response;
            this.userName = userName;
            this.date = date;
        }
    }
}
