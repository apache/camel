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
    
    protected void verifyGradeA(String endpointUri) throws Exception {       
        gradeA.reset();
        otherGrade.reset();
        gradeA.expectedMessageCount(1);
        otherGrade.expectedMessageCount(0);
        template.sendBody(endpointUri, new Student(95));
        assertMockEndpointsSatisfied();
    }
    
    public void verifyOtherGrade(String endpointUri) throws Exception {
        gradeA.reset();
        otherGrade.reset();
        gradeA.expectedMessageCount(0);
        otherGrade.expectedMessageCount(1);
        template.sendBody(endpointUri, new Student(60));
        assertMockEndpointsSatisfied();
    }
    
    public void testBeanExpression() throws Exception {
        verifyGradeA("direct:expression");
        verifyOtherGrade("direct:expression");
    }
    
    public void testMethod() throws Exception {
        verifyGradeA("direct:method");
        verifyOtherGrade("direct:method");
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
                from("direct:expression")
                    .choice()
                        .when().expression(method(MyBean.class, "isGradeA")).to("mock:gradeA")
                        .otherwise().to("mock:otherGrade")
                    .end();
                
                from("direct:method")
                    .choice()
                        .when().method(MyBean.class).to("mock:gradeA")
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
        
        Student(int grade) {
            this.grade = grade;
        }
        
        public int getGrade() {
            return grade;
        }
    }

}
