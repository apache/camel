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
package org.apache.camel.component.mapstruct;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

@UriEndpoint(firstVersion = "3.19.0", scheme = "mapstruct", title = "MapStruct", syntax = "mapstruct:className",
             remote = false, producerOnly = true,
             category = { Category.TRANSFORMATION })
public class MapstructEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String className;
    private transient Class<?> clazz;
    @UriParam(defaultValue = "true")
    private boolean mandatory = true;

    public MapstructEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MapstructProducer(this, clazz, mandatory);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported");
    }

    public String getClassName() {
        return className;
    }

    /**
     * The fully qualified class name of the POJO that mapstruct should convert to (target)
     */
    public void setClassName(String className) {
        this.className = className;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * Whether there must exist a mapstruct converter to convert to the POJO.
     */
    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    @Override
    protected void doBuild() throws Exception {
        ObjectHelper.notNull(className, "className");
        clazz = getCamelContext().getClassResolver().resolveMandatoryClass(className);
    }
}
