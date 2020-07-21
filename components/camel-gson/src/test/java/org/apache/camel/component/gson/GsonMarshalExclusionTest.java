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
package org.apache.camel.component.gson;

import java.util.Arrays;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.gson.annotation.ExcludeAge;
import org.apache.camel.component.gson.annotation.ExcludeWeight;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GsonMarshalExclusionTest extends CamelTestSupport {

    @Test
    public void testMarshalAndUnmarshalPojoWithExclusion() throws Exception {

        TestPojoExclusion in = new TestPojoExclusion();

        MockEndpoint mock = getMockEndpoint("mock:reversePojoExcludeWeight");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(TestPojoExclusion.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:inPojoExcludeWeight", in);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("{\"age\":30,\"height\":190}", marshalledAsString);

        template.sendBody("direct:backPojoExcludeWeight", marshalled);

        mock.assertIsSatisfied();
    }

    @Test
    public void testMarshalAndUnmarshalPojoWithAnotherExclusion() throws Exception {

        TestPojoExclusion in = new TestPojoExclusion();

        MockEndpoint mock = getMockEndpoint("mock:reversePojoExcludeAge");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(TestPojoExclusion.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:inPojoExcludeAge", in);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("{\"height\":190,\"weight\":70}", marshalledAsString);

        template.sendBody("direct:backPojoExcludeAge", marshalled);

        mock.assertIsSatisfied();
    }

    /**
     * Strategy to exclude {@link ExcludeWeight} annotated fields
     */
    protected static class WeightExclusionStrategy implements ExclusionStrategy {

        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getAnnotation(ExcludeWeight.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }

    //START SNIPPET: strategy
    /**
     * Strategy to exclude {@link ExcludeAge} annotated fields
     */
    protected static class AgeExclusionStrategy implements ExclusionStrategy {

        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getAnnotation(ExcludeAge.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }
    //END SNIPPET: strategy

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {

                GsonDataFormat weightExclusionFormat = new GsonDataFormat(TestPojoExclusion.class);
                weightExclusionFormat.setExclusionStrategies(Arrays.<ExclusionStrategy>asList(new WeightExclusionStrategy()));
                from("direct:inPojoExcludeWeight").marshal(weightExclusionFormat);
                from("direct:backPojoExcludeWeight").unmarshal(weightExclusionFormat).to("mock:reversePojoExcludeWeight");

                //START SNIPPET: format
                GsonDataFormat ageExclusionFormat = new GsonDataFormat(TestPojoExclusion.class);
                ageExclusionFormat.setExclusionStrategies(Arrays.<ExclusionStrategy>asList(new AgeExclusionStrategy()));
                from("direct:inPojoExcludeAge").marshal(ageExclusionFormat);
                //END SNIPPET: format
                from("direct:backPojoExcludeAge").unmarshal(ageExclusionFormat).to("mock:reversePojoExcludeAge");
            }
        };
    }

}
