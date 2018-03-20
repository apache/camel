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
package org.apache.camel.component.aws.xray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.aws.xray.TestDataBuilder.TestSegment;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestSubsegment;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestTrace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class TestUtils {

    private TestUtils() {

    }

    public static void checkData(Map<String, TestTrace> receivedData, List<TestTrace> testData) {
        assertThat("Incorrect number of traces",
                receivedData.size(), is(equalTo(testData.size())));
        int i = 0;
        for (String key : receivedData.keySet()) {
            TestTrace trace = receivedData.get(key);
            verifyTraces(testData.get(i++), trace);
        }
    }

    private static void verifyTraces(TestTrace expected, TestTrace actual) {
        assertThat("Incorrect number of segment for trace",
                actual.getSegments().size(), is(equalTo(expected.getSegments().size())));
        List<TestSegment> expectedSegments = new ArrayList<>(expected.getSegments());
        List<TestSegment> actualSegments = new ArrayList<>(actual.getSegments());

        boolean randomOrder = expected.isRandomOrder();
        for (int i = 0; i < expected.getSegments().size(); i++) {

            if (randomOrder) {
                for (TestSegment expectedSeg : expectedSegments) {
                    boolean found = false;
                    for (TestSegment actualSeg : actualSegments) {
                        if (expectedSeg.getName().equals(actualSeg.getName())) {
                            found = true;
                            verifySegments(expectedSeg, actualSeg);
                            break;
                        }
                    }
                    if (!found) {
                        fail("Could not find expected segment " + expectedSeg.getName());
                    }
                }
            } else {
                verifySegments(expectedSegments.get(i), actualSegments.get(i));
            }
        }
    }

    private static void verifySegments(TestSegment expected, TestSegment actual) {
        assertThat("Incorrect name of segment",
                actual.getName(), is(equalTo(expected.getName())));

        boolean randomOrder = expected.isRandomOrder();
        if (!expected.getSubsegments().isEmpty()) {
            if (randomOrder) {
                checkSubsegmentInRandomOrder(expected.getSubsegments(), actual.getSubsegments());
            } else {
                for (int i = 0; i < expected.getSubsegments().size(); i++) {
                    if (actual.getName().equals(expected.getName())) {
                        assertThat("An expected subsegment is missing in the actual payload of segment " + actual.getName(),
                                actual.getSubsegments().size(), is(greaterThanOrEqualTo(expected.getSubsegments().size())));
                        verifySubsegments(expected.getSubsegments().get(i), actual.getSubsegments().get(i));
                    }
                }
            }
        }
        if (!expected.getAnnotations().isEmpty()) {
            verifyAnnotations(expected.getAnnotations(), actual.getAnnotations());
        }
        if (!expected.getMetadata().isEmpty()) {
            verifyMetadata(expected.getMetadata(), actual.getMetadata());
        }
    }

    private static void verifySubsegments(TestSubsegment expected, TestSubsegment actual) {
        assertThat("Incorrect name of subsegment",
                actual.getName(), is(equalTo(expected.getName())));

        boolean randomOrder = expected.isRandomOrder();
        if (!expected.getSubsegments().isEmpty()) {
            if (randomOrder) {
                checkSubsegmentInRandomOrder(expected.getSubsegments(), actual.getSubsegments());
            } else {
                for (int i = 0; i < expected.getSubsegments().size(); i++) {
                    verifySubsegments(expected.getSubsegments().get(i), actual.getSubsegments().get(i));
                }
            }
        }
        if (!expected.getAnnotations().isEmpty()) {
            verifyAnnotations(expected.getAnnotations(), actual.getAnnotations());
        }
        if (!expected.getMetadata().isEmpty()) {
            verifyMetadata(expected.getMetadata(), actual.getMetadata());
        }
    }

    private static void checkSubsegmentInRandomOrder(List<TestSubsegment> expectedSubs, List<TestSubsegment> actualSubs) {
        for (TestSubsegment expectedSub : expectedSubs) {
            boolean found = false;
            for (TestSubsegment actualSub : actualSubs) {
                if (expectedSub.getName().equals(actualSub.getName())) {
                    found = true;
                    verifySubsegments(expectedSub, actualSub);
                    break;
                }
            }
            if (!found) {
                fail("Could not find expected sub-segment " + expectedSub.getName());
            }
        }
    }

    private static void verifyAnnotations(Map<String, Object> expected, Map<String, Object> actual) {
        assertThat(actual.size(), is(equalTo(expected.size())));
        for (String key : expected.keySet()) {
            assertTrue("Annotation " + key + " is missing", actual.containsKey(key));
            assertThat("Annotation value of " + key + " is different",
                    actual.get(key), is(equalTo(expected.get(key))));
        }
    }

    private static void verifyMetadata(Map<String, Map<String, Object>> expected,
                                       Map<String, Map<String, Object>> actual) {

        assertThat("Insufficient number of metadata found",
                actual.size(), is(greaterThanOrEqualTo(expected.size())));
        for (String namespace : expected.keySet()) {
            assertTrue("Namespace " + namespace + " not found in metadata",
                    actual.containsKey(namespace));
            for (String key : expected.get(namespace).keySet()) {
                assertTrue("Key " + key + " of namespace + " + namespace + " not found",
                        actual.get(namespace).containsKey(key));
                assertThat("Incorrect value of key " + key + " in namespace " + namespace,
                        actual.get(namespace).get(key), is(equalTo(expected.get(namespace).get(key))));
            }
        }
    }
}