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

import org.apache.camel.dataformat.bindy.FormattingOptions;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class DefaultFactoryRegistryTest {

    @Test(expected = IllegalArgumentException.class)
    public void unregisterFormatFactory() {
        FactoryRegistry reg = new DefaultFactoryRegistry();
        FormattingOptions formattingOptions = new FormattingOptions().forClazz(String.class);
        
        assertNotNull(reg.findForFormattingOptions(formattingOptions));
        
        reg.unregister(StringFormatFactory.class);        
        reg.findForFormattingOptions(formattingOptions);
    }
}