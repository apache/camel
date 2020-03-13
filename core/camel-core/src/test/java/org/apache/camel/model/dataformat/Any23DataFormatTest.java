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
package org.apache.camel.model.dataformat;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class Any23DataFormatTest {

    private final String xml = "<?xml version=\"1.0\"?>"
        + "<any23 xmlns=\"http://camel.apache.org/schema/spring\">"
        + "<configuration key=\"k1\" value=\"v1\" />"
        + "<configuration key=\"k2\" value=\"v2\" />"
        + "</any23>";

    @Test
    public void shouldDeserializeConfigurationPropertiesFromXml() throws JAXBException, IOException {
        final JAXBContext context = JAXBContext.newInstance(Any23DataFormat.class);

        final Unmarshaller unmarshaller = context.createUnmarshaller();

        final StringReader reader = new StringReader(xml);
        final Any23DataFormat any23DataFormat = (Any23DataFormat) unmarshaller.unmarshal(reader);

        assertThat(any23DataFormat.getConfigurationAsMap()).containsOnly(entry("k1", "v1"), entry("k2", "v2"));
    }
}
