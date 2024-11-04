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
package org.apache.camel.component.fury;

import java.io.*;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.fury.BaseFury;
import org.apache.fury.Fury;
import org.apache.fury.config.FuryBuilder;
import org.apache.fury.config.Language;
import org.apache.fury.io.FuryInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialize and deserialize messages using <a href="https://fury.apache.org">Apache Fury</a>
 */
@Dataformat("fury")
@Metadata(firstVersion = "4.9.0", title = "Fury")
public class FuryDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(FuryDataFormat.class);

    private CamelContext camelContext;
    private Class<?> unmarshalType;
    private String unmarshalTypeName;
    private boolean requireClassRegistration = true;
    private boolean threadSafe = true;
    private boolean allowAutoWiredFury = true;

    private BaseFury fury;

    public FuryDataFormat() {
        this(Object.class);
    }

    public FuryDataFormat(Class<?> unmarshalType) {
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
        return "fury";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        fury.serialize(stream, graph);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return fury.deserialize(new FuryInputStream(stream));
    }

    @Override
    protected void doInit() throws Exception {
        if (unmarshalTypeName != null && (unmarshalType == null || unmarshalType == Object.class)) {
            unmarshalType = camelContext.getClassResolver().resolveClass(unmarshalTypeName);
        }

        if (fury == null && isAllowAutoWiredFury()) {
            fury = getCamelContext().getRegistry().findSingleByType(BaseFury.class);
        }

        if (fury == null) {
            FuryBuilder builder = Fury.builder().withLanguage(Language.JAVA);
            builder.requireClassRegistration(requireClassRegistration);
            fury = threadSafe ? builder.buildThreadSafeFury() : builder.build();
        }

        if (unmarshalType != null) {
            fury.register(unmarshalType);
        }
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    // Properties
    // -------------------------------------------------------------------------
    public BaseFury getFury() {
        return fury;
    }

    public void setFury(BaseFury fury) {
        this.fury = fury;
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

    public boolean isAllowAutoWiredFury() {
        return allowAutoWiredFury;
    }

    public void setAllowAutoWiredFury(boolean allowAutoWiredFury) {
        this.allowAutoWiredFury = allowAutoWiredFury;
    }
}
