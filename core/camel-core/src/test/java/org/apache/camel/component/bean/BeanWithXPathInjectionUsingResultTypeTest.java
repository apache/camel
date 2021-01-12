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
import org.apache.camel.language.xpath.XPath;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanWithXPathInjectionUsingResultTypeTest extends ContextTestSupport {

    protected MyBean myBean = new MyBean();

    @Test
    public void testSendMessage() throws Exception {
        template.sendBody("bean:myBean", "<a><b>12</b></a>");
        assertEquals("12", myBean.ab, "bean ab: " + myBean);
        assertEquals("a12", myBean.abText, "bean abText: " + myBean);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();

        answer.bind("myBean", myBean);
        return answer;
    }

    public static class MyBean {
        public String ab;
        public String abText;

        public void read(
                @XPath("//a/b/text()") String ab,
                @XPath(value = "concat('a',//a/b)", resultType = String.class) String abText) {
            this.ab = ab;
            this.abText = abText;
        }
    }
}
