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
package org.apache.camel.reifier.dataformat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.dataformat.ASN1DataFormat;
import org.apache.camel.model.dataformat.Any23DataFormat;
import org.apache.camel.model.dataformat.AvroDataFormat;
import org.apache.camel.model.dataformat.BarcodeDataFormat;
import org.apache.camel.model.dataformat.Base64DataFormat;
import org.apache.camel.model.dataformat.BeanioDataFormat;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.CBORDataFormat;
import org.apache.camel.model.dataformat.CryptoDataFormat;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.model.dataformat.FhirDataformat;
import org.apache.camel.model.dataformat.FhirJsonDataFormat;
import org.apache.camel.model.dataformat.FhirXmlDataFormat;
import org.apache.camel.model.dataformat.FlatpackDataFormat;
import org.apache.camel.model.dataformat.GrokDataFormat;
import org.apache.camel.model.dataformat.GzipDataFormat;
import org.apache.camel.model.dataformat.HL7DataFormat;
import org.apache.camel.model.dataformat.IcalDataFormat;
import org.apache.camel.model.dataformat.JacksonXMLDataFormat;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonApiDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.LZFDataFormat;
import org.apache.camel.model.dataformat.MimeMultipartDataFormat;
import org.apache.camel.model.dataformat.PGPDataFormat;
import org.apache.camel.model.dataformat.ProtobufDataFormat;
import org.apache.camel.model.dataformat.RssDataFormat;
import org.apache.camel.model.dataformat.SoapJaxbDataFormat;
import org.apache.camel.model.dataformat.SyslogDataFormat;
import org.apache.camel.model.dataformat.TarFileDataFormat;
import org.apache.camel.model.dataformat.ThriftDataFormat;
import org.apache.camel.model.dataformat.TidyMarkupDataFormat;
import org.apache.camel.model.dataformat.UniVocityCsvDataFormat;
import org.apache.camel.model.dataformat.UniVocityFixedWidthDataFormat;
import org.apache.camel.model.dataformat.UniVocityTsvDataFormat;
import org.apache.camel.model.dataformat.XMLSecurityDataFormat;
import org.apache.camel.model.dataformat.XStreamDataFormat;
import org.apache.camel.model.dataformat.XmlRpcDataFormat;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.ZipDeflaterDataFormat;
import org.apache.camel.model.dataformat.ZipFileDataFormat;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerAware;
import org.apache.camel.spi.ReifierStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DataFormatReifier<T extends DataFormatDefinition> extends AbstractReifier {

    private static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/configurer/";

    private static final Logger LOG = LoggerFactory.getLogger(DataFormatReifier.class);

    private static final Map<Class<? extends DataFormatDefinition>, BiFunction<CamelContext, DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>>> DATAFORMATS;
    static {
        Map<Class<? extends DataFormatDefinition>, BiFunction<CamelContext, DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>>> map = new HashMap<>();
        map.put(Any23DataFormat.class, Any23DataFormatReifier::new);
        map.put(ASN1DataFormat.class, ASN1DataFormatReifier::new);
        map.put(AvroDataFormat.class, AvroDataFormatReifier::new);
        map.put(BarcodeDataFormat.class, BarcodeDataFormatReifier::new);
        map.put(Base64DataFormat.class, Base64DataFormatReifier::new);
        map.put(BeanioDataFormat.class, BeanioDataFormatReifier::new);
        map.put(BindyDataFormat.class, BindyDataFormatReifier::new);
        map.put(CBORDataFormat.class, CBORDataFormatReifier::new);
        map.put(CryptoDataFormat.class, CryptoDataFormatReifier::new);
        map.put(CsvDataFormat.class, CsvDataFormatReifier::new);
        map.put(CustomDataFormat.class, CustomDataFormatReifier::new);
        map.put(FhirDataformat.class, FhirDataFormatReifier::new);
        map.put(FhirJsonDataFormat.class, FhirJsonDataFormatReifier::new);
        map.put(FhirXmlDataFormat.class, FhirXmlDataFormatReifier::new);
        map.put(FlatpackDataFormat.class, FlatpackDataFormatReifier::new);
        map.put(GrokDataFormat.class, GrokDataFormatReifier::new);
        map.put(GzipDataFormat.class, GzipDataFormatReifier::new);
        map.put(HL7DataFormat.class, HL7DataFormatReifier::new);
        map.put(IcalDataFormat.class, IcalDataFormatReifier::new);
        map.put(JacksonXMLDataFormat.class, JacksonXMLDataFormatReifier::new);
        map.put(JaxbDataFormat.class, JaxbDataFormatReifier::new);
        map.put(JsonApiDataFormat.class, JsonApiDataFormatReifier::new);
        map.put(JsonDataFormat.class, JsonDataFormatReifier::new);
        map.put(LZFDataFormat.class, LZFDataFormatReifier::new);
        map.put(MimeMultipartDataFormat.class, MimeMultipartDataFormatReifier::new);
        map.put(PGPDataFormat.class, PGPDataFormatReifier::new);
        map.put(ProtobufDataFormat.class, ProtobufDataFormatReifier::new);
        map.put(RssDataFormat.class, RssDataFormatReifier::new);
        map.put(SoapJaxbDataFormat.class, SoapJaxbDataFormatReifier::new);
        map.put(SyslogDataFormat.class, SyslogDataFormatReifier::new);
        map.put(TarFileDataFormat.class, TarFileDataFormatReifier::new);
        map.put(ThriftDataFormat.class, ThriftDataFormatReifier::new);
        map.put(TidyMarkupDataFormat.class, TidyMarkupDataFormatReifier::new);
        map.put(UniVocityCsvDataFormat.class, UniVocityCsvDataFormatReifier::new);
        map.put(UniVocityFixedWidthDataFormat.class, UniVocityFixedWidthDataFormatReifier::new);
        map.put(UniVocityTsvDataFormat.class, UniVocityTsvDataFormatReifier::new);
        map.put(XmlRpcDataFormat.class, XmlRpcDataFormatReifier::new);
        map.put(XMLSecurityDataFormat.class, XMLSecurityDataFormatReifier::new);
        map.put(XStreamDataFormat.class, XStreamDataFormatReifier::new);
        map.put(YAMLDataFormat.class, YAMLDataFormatReifier::new);
        map.put(ZipDeflaterDataFormat.class, ZipDataFormatReifier::new);
        map.put(ZipFileDataFormat.class, ZipFileDataFormatReifier::new);
        DATAFORMATS = map;
        ReifierStrategy.addReifierClearer(DataFormatReifier::clearReifiers);
    }

    protected final T definition;

    public DataFormatReifier(CamelContext camelContext, T definition) {
        super(camelContext);
        this.definition = definition;
    }

    public static void registerReifier(Class<? extends DataFormatDefinition> dataFormatClass,
                                       BiFunction<CamelContext, DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>> creator) {
        DATAFORMATS.put(dataFormatClass, creator);
    }

    public static void clearReifiers() {
        DATAFORMATS.clear();
    }

    public static DataFormat getDataFormat(CamelContext camelContext, DataFormatDefinition type) {
        return getDataFormat(camelContext, ObjectHelper.notNull(type, "type"), null);
    }

    public static DataFormat getDataFormat(CamelContext camelContext, String ref) {
        return getDataFormat(camelContext, null, ObjectHelper.notNull(ref, "ref"));
    }

    /**
     * Factory method to create the data format
     *
     * @param camelContext the camel context
     * @param type the data format type
     * @param ref reference to lookup for a data format
     * @return the data format or null if not possible to create
     */
    public static DataFormat getDataFormat(CamelContext camelContext, DataFormatDefinition type, String ref) {
        if (type == null) {
            ObjectHelper.notNull(ref, "ref or type");

            DataFormat dataFormat = CamelContextHelper.lookup(camelContext, ref, DataFormat.class);
            if (dataFormat != null) {
                return dataFormat;
            }

            // try to let resolver see if it can resolve it, its not always
            // possible
            type = camelContext.getExtension(Model.class).resolveDataFormatDefinition(ref);

            if (type == null) {
                dataFormat = camelContext.resolveDataFormat(ref);
                if (dataFormat == null) {
                    throw new IllegalArgumentException("Cannot find data format in registry with ref: " + ref);
                }

                return dataFormat;
            }
        }
        if (type.getDataFormat() != null) {
            return type.getDataFormat();
        }
        return reifier(camelContext, type).createDataFormat();
    }

    public static DataFormatReifier<? extends DataFormatDefinition> reifier(CamelContext camelContext, DataFormatDefinition definition) {
        BiFunction<CamelContext, DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>> reifier = DATAFORMATS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(camelContext, definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
    }

    public DataFormat createDataFormat() {
        DataFormat dataFormat = definition.getDataFormat();
        if (dataFormat == null) {
            dataFormat = doCreateDataFormat();
            if (dataFormat != null) {
                if (dataFormat instanceof DataFormatContentTypeHeader) {
                    // is enabled by default so assume true if null
                    final boolean contentTypeHeader = parseBoolean(definition.getContentTypeHeader(), true);
                    ((DataFormatContentTypeHeader) dataFormat).setContentTypeHeader(contentTypeHeader);
                }
                // configure the rest of the options
                configureDataFormat(dataFormat);
            } else {
                throw new IllegalArgumentException("Data format '" + (definition.getDataFormatName() != null ? definition.getDataFormatName() : "<null>")
                                                   + "' could not be created. "
                                                   + "Ensure that the data format is valid and the associated Camel component is present on the classpath");
            }
        }
        return dataFormat;
    }

    /**
     * Factory method to create the data format instance
     */
    protected DataFormat doCreateDataFormat() {
        // must use getDataFormatName() as we need special logic in json dataformat
        String dfn = definition.getDataFormatName();
        if (dfn != null) {
            return camelContext.createDataFormat(dfn);
        }
        return null;
    }

    private String getDataFormatName() {
        return definition.getDataFormatName();
    }

    /**
     * Allows derived classes to customize the data format
     */
    protected void configureDataFormat(DataFormat dataFormat) {
        Map<String, Object> properties = new LinkedHashMap<>();
        prepareDataFormatConfig(properties);
        properties.entrySet().removeIf(e -> e.getValue() == null);

        PropertyConfigurer configurer = findPropertyConfigurer(dataFormat);

        PropertyBindingSupport.build()
                .withCamelContext(camelContext)
                .withTarget(dataFormat)
                .withReference(true)
                .withMandatory(true)
                .withConfigurer(configurer)
                .withProperties(properties)
                .bind();
    }

    private PropertyConfigurer findPropertyConfigurer(DataFormat dataFormat) {
        PropertyConfigurer configurer = null;
        String name = getDataFormatName();
        LOG.trace("Discovering optional dataformat property configurer class for dataformat: {}", name);
        if (dataFormat instanceof PropertyConfigurerAware) {
            configurer = ((PropertyConfigurerAware) dataFormat).getPropertyConfigurer(dataFormat);
            if (LOG.isDebugEnabled() && configurer != null) {
                LOG.debug("Discovered dataformat property configurer using the PropertyConfigurerAware: {} -> {}", name, configurer);
            }
        }
        if (configurer == null) {
            final String configurerName = name + "-dataformat-configurer";
            configurer = lookup(configurerName, PropertyConfigurer.class);
            if (LOG.isDebugEnabled() && configurer != null) {
                LOG.debug("Discovered dataformat property configurer using the Camel registry: {} -> {}", configurerName, configurer);
            }
        }
        if (configurer == null) {
            Class<?> clazz = camelContext.adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH)
                    .findOptionalClass(name + "-dataformat-configurer", null)
                    .orElse(null);
            if (clazz != null) {
                configurer = org.apache.camel.support.ObjectHelper.newInstance(clazz, PropertyConfigurer.class);
                if (LOG.isDebugEnabled() && configurer != null) {
                    LOG.debug("Discovered dataformat property configurer using the FactoryFinder: {} -> {}", name, configurer);
                }
            }
        }
        return configurer;
    }

    protected abstract void prepareDataFormatConfig(Map<String, Object> properties);

}
