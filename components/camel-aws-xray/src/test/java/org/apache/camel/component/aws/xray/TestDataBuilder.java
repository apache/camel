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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@SuppressWarnings({"WeakerAccess", "unchecked"})
final class TestDataBuilder {

    private TestDataBuilder() {

    }

    static class TestTrace {

        private boolean randomOrder;
        private Set<TestSegment> segments = new TreeSet<>((TestSegment seg1, TestSegment seg2) -> {
            if (seg1.equals(seg2)) {
                return 0;
            }
            if (seg1.startTime != 0 && seg2.startTime != 0) {
                if (seg1.startTime == seg2.startTime) {
                    return -1;
                }
                return seg1.startTime < seg2.startTime ? -1 : 1;
            } else {
                return 1;
            }
        });

        public TestTrace withSegment(TestSegment segment) {
            this.segments.add(segment);
            return this;
        }

        public Set<TestSegment> getSegments() {
            return segments;
        }

        public TestTrace inRandomOrder() {
            randomOrder = true;
            return this;
        }

        public boolean isRandomOrder() {
            return randomOrder;
        }
    }

    public abstract static class TestEntity<T> {
        protected String name;
        protected Map<String, Object> annotations = new LinkedHashMap<>();
        protected Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();
        protected List<TestSubsegment> subsegments = new ArrayList<>();
        protected boolean randomOrder;

        protected TestEntity(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getAnnotations() {
            return this.annotations;
        }

        public Map<String, Map<String, Object>> getMetadata() {
            return metadata;
        }

        public List<TestSubsegment> getSubsegments() {
            return subsegments;
        }

        public T withAnnotation(String name, Object value) {
            this.annotations.put(name, value);
            return (T) this;
        }

        public T withMetadata(String name, Object value) {
            return this.withMetadata("default", name, value);
        }

        public T withMetadata(String namespace, String name, Object value) {
            if (!this.metadata.containsKey(namespace)) {
                this.metadata.put(namespace, new LinkedHashMap<>());
            }
            Map<String, Object> namespaceMap = this.metadata.get(namespace);
            namespaceMap.put(name, value);
            return (T) this;
        }

        public T withSubsegment(TestSubsegment subsegment) {
            this.subsegments.add(subsegment);
            return (T) this;
        }

        public T inRandomOrder() {
            this.randomOrder = true;
            return (T) this;
        }

        public boolean isRandomOrder() {
            return randomOrder;
        }

        @Override
        public String toString() {
            String ret = this.getClass().getSimpleName() + "(name: " + name;

            if (!subsegments.isEmpty()) {
                ret += ", subsegments: [";
                StringBuilder sb = new StringBuilder();
                for (TestSubsegment sub : subsegments) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(sub);
                }
                ret += sb.toString() + "]";
            }
            if (!annotations.isEmpty()) {
                ret += ", annotations: {";
                StringBuilder sb = new StringBuilder();
                for (String key : annotations.keySet()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(key).append("->").append(annotations.get(key));
                }
                ret += sb.toString() + "}";
            }
            if (!metadata.isEmpty()) {
                ret += ", metadata: {";
                StringBuilder sb = new StringBuilder();
                for (String namespace : metadata.keySet()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(namespace).append(": [");
                    boolean first = true;
                    for (String key : metadata.get(namespace).keySet()) {
                        if (!first) {
                            sb.append(", ");
                        }
                        sb.append(key).append("->").append(metadata.get(namespace).get(key));
                        first = false;
                    }
                    sb.append("]");
                }
                ret += sb.toString() + "}";
            }
            ret += ")";
            return ret;
        }
    }

    static class TestSegment extends TestEntity<TestSegment> {
        private double startTime;

        public TestSegment(String name) {
            super(name);
        }

        public TestSegment(String name, double startTime) {
            this(name);
            this.startTime = startTime;
        }

        public double getStartTime() {
            return this.startTime;
        }
    }

    static class TestSubsegment extends TestEntity<TestSubsegment> {

        public TestSubsegment(String name) {
            super(name);
        }
    }

    public static TestTrace createTrace() {
        return new TestTrace();
    }

    public static TestSegment createSegment(String name) {
        return new TestSegment(name);
    }

    public static TestSubsegment createSubsegment(String name) {
        return new TestSubsegment(name);
    }
}