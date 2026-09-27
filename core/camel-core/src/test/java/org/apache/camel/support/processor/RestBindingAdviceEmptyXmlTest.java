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
package org.apache.camel.support.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestBindingAdviceEmptyXmlTest {

    @Test
    public void testEmptyRootElement() {
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<a/>")).isTrue();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<a></a>")).isTrue();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<order></order>")).isTrue();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<?xml version=\"1.0\"?><a/>")).isTrue();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<?xml version=\"1.0\"?>\n<a></a>")).isTrue();
    }

    @Test
    public void testNotEmptyRootElement() {
        assertThat(RestBindingAdvice.isEmptyXmlRootElement(null)).isFalse();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("")).isFalse();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<a>x</a>")).isFalse();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<a><b/></a>")).isFalse();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("<?xml version=\"1.0\"?><a>x</a>")).isFalse();
        assertThat(RestBindingAdvice.isEmptyXmlRootElement("not xml")).isFalse();
    }
}
