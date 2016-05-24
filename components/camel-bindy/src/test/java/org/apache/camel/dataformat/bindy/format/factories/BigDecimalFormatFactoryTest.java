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
package org.apache.camel.dataformat.bindy.format.factories;

import java.math.BigDecimal;
import org.apache.camel.dataformat.bindy.FormattingOptions;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class BigDecimalFormatFactoryTest {

    private FormattingOptions formattingOptions = new FormattingOptions()
            .forClazz(BigDecimal.class);
    private FormattingOptions wrongClass = new FormattingOptions()
            .forClazz(Integer.class);
    private FormattingOptions hasPattern = new FormattingOptions()
            .forClazz(BigDecimal.class)
            .withPattern("999.99");

    @Test
    public void canBuild() throws Exception {
        assertThat(new BigDecimalFormatFactory().canBuild(formattingOptions), is(true));
        assertThat(new BigDecimalFormatFactory().canBuild(wrongClass), is(false));
        assertThat(new BigDecimalFormatFactory().canBuild(hasPattern), is(false));
    }

    @Test
    public void build() throws Exception {

    }

}