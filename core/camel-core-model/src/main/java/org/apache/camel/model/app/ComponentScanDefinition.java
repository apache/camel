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
package org.apache.camel.model.app;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultRegistry;

/**
 * <p>
 * An equivalent of Spring's {@code <context:component-scan>} element that can be used to populate underlying
 * bean registry.
 * </p>
 * <p>
 * With Spring application, the bean registry is provided by Spring itself, but if we want to use Camel without
 * Spring, we have an option to use {@link DefaultRegistry} with underlying, supporting bean
 * {@link org.apache.camel.spi.Registry registries} and {@link org.apache.camel.spi.BeanRepository repositories}.
 * </p>
 */
@Metadata(label = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public class ComponentScanDefinition {

    @XmlAttribute(name = "base-package")
    private String basePackage;

    /** Whether to use {@code jakarta.inject} annotations like {@code @Inject} or {@code @Named} */
    @XmlAttribute(name = "use-jsr-330")
    private boolean useJsr330 = true;

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public void setUseJsr330(boolean useJsr330) {
        this.useJsr330 = useJsr330;
    }

    public boolean isJsr330Enabled() {
        return useJsr330;
    }

}
