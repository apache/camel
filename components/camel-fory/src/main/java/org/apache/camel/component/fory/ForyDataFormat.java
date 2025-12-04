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

package org.apache.camel.component.fory;

import java.io.*;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.fory.BaseFory;
import org.apache.fory.Fory;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.config.Language;
import org.apache.fory.io.ForyInputStream;

/**
 * Serialize and deserialize messages using <a href="https://fory.apache.org">Apache Fory</a>
 */
@Dataformat("fory")
@Metadata(firstVersion = "4.9.0", title = "Fory")
public class ForyDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private CamelContext camelContext;
    private Class<?> unmarshalType;
    private String unmarshalTypeName;
    private boolean requireClassRegistration = true;
    private boolean threadSafe = true;
    private boolean allowAutoWiredFory = true;
    private BaseFory fory;

    public ForyDataFormat() {
        this(Object.class);
    }

    public ForyDataFormat(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getDataFormatName() {
        return "fory";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        fory.serialize(stream, graph);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return fory.deserialize(new ForyInputStream(stream));
    }

    @Override
    protected void doInit() throws Exception {
        if (unmarshalTypeName != null && (unmarshalType == null || unmarshalType == Object.class)) {
            unmarshalType = camelContext.getClassResolver().resolveClass(unmarshalTypeName);
        }

        if (fory == null && isAllowAutoWiredFory()) {
            fory = getCamelContext().getRegistry().findSingleByType(BaseFory.class);
        }

        if (fory == null) {
            ForyBuilder builder = Fory.builder().withLanguage(Language.JAVA);
            builder.requireClassRegistration(requireClassRegistration);
            fory = threadSafe ? builder.buildThreadSafeFory() : builder.build();
        }

        if (unmarshalType != null) {
            fory.register(unmarshalType);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public BaseFory getFory() {
        return fory;
    }

    public void setFory(BaseFory fory) {
        this.fory = fory;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public boolean isRequireClassRegistration() {
        return requireClassRegistration;
    }

    public void setRequireClassRegistration(boolean requireClassRegistration) {
        this.requireClassRegistration = requireClassRegistration;
    }

    public boolean isThreadSafe() {
        return threadSafe;
    }

    public void setThreadSafe(boolean threadSafe) {
        this.threadSafe = threadSafe;
    }

    public boolean isAllowAutoWiredFory() {
        return allowAutoWiredFory;
    }

    public void setAllowAutoWiredFory(boolean allowAutoWiredFory) {
        this.allowAutoWiredFory = allowAutoWiredFory;
    }
}
