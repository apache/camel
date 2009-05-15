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

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class ChoiceWhenBeanExpressionTest extends ContextTestSupport {
    private MockEndpoint gradeA;
    private MockEndpoint otherGrade;
    
    public void testGradeA() throws Exception {       
        gradeA.expectedMessageCount(1);
        otherGrade.expectedMessageCount(0);
        template.sendBody("direct:start", new Student(95));
        assertMockEndpointsSatisfied();
    }
    
    public void testOtherGrade() throws Exception {       
        gradeA.expectedMessageCount(0);
        otherGrade.expectedMessageCount(1);
        template.sendBody("direct:start", new Student(60));
        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        gradeA = getMockEndpoint("mock:gradeA");
        otherGrade = getMockEndpoint("mock:otherGrade");
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {                
                from("direct:start")
                    .choice()
                        .when().expression(bean(MyBean.class, "isGradeA")).to("mock:gradeA")
                        .otherwise().to("mock:otherGrade")
                    .end();
            }
        };
    }
    
    
    public static class MyBean {
        public boolean isGradeA(@Body Student student) {
            return student.getGrade() >= 90;            
        }
    }
    
    class Student {
        private int grade;
        
        public Student(int grade) {
            this.grade = grade;
        }
        
        public int getGrade() {
            return grade;
        }
    }

}
