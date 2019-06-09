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
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.dataformat.Any23DataFormat;
import org.apache.camel.model.dataformat.ASN1DataFormat;
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
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.support.EndpointHelper.isReferenceParameter;

public abstract class DataFormatReifier<T extends DataFormatDefinition> {

    private static final Map<Class<? extends DataFormatDefinition>, Function<DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>>> DATAFORMATS;
    static {
        Map<Class<? extends DataFormatDefinition>, Function<DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>>> map = new HashMap<>();
        map.put(Any23DataFormat.class,Any23DataFormatReifier::new);
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
    }

    protected final T definition;

    public DataFormatReifier(T definition) {
        this.definition = definition;
    }

    public static void registerReifier(Class<? extends DataFormatDefinition> dataFormatClass, Function<DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>> creator) {
        DATAFORMATS.put(dataFormatClass, creator);
    }

    /**
     * Factory method to create the data format
     *
     * @param camelContext the camel context
     * @param type         the data format type
     * @param ref          reference to lookup for a data format
     * @return the data format or null if not possible to create
     */
    public static DataFormat getDataFormat(CamelContext camelContext, DataFormatDefinition type, String ref) {
        if (type == null) {
            ObjectHelper.notNull(ref, "ref or type");

            DataFormat dataFormat = camelContext.getRegistry().lookupByNameAndType(ref, DataFormat.class);
            if (dataFormat != null) {
                return dataFormat;
            }

            // try to let resolver see if it can resolve it, its not always possible
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
        return reifier(type).createDataFormat(camelContext);
    }

    public static DataFormatReifier<? extends DataFormatDefinition> reifier(DataFormatDefinition definition) {
        Function<DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>> reifier = DATAFORMATS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
    }

    public DataFormat createDataFormat(CamelContext camelContext) {
        DataFormat dataFormat = definition.getDataFormat();
        if (dataFormat == null) {
            Runnable propertyPlaceholdersChangeReverter = ProcessorDefinitionHelper.createPropertyPlaceholdersChangeReverter();

            // resolve properties before we create the data format
            try {
                ProcessorDefinitionHelper.resolvePropertyPlaceholders(camelContext, definition);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error resolving property placeholders on data format: " + definition, e);
            }
            try {
                dataFormat = doCreateDataFormat(camelContext);
                if (dataFormat != null) {
                    // is enabled by default so assume true if null
                    final boolean contentTypeHeader = definition.getContentTypeHeader() == null || definition.getContentTypeHeader();
                    try {
                        setProperty(camelContext, dataFormat, "contentTypeHeader", contentTypeHeader);
                    } catch (Exception e) {
                        // ignore as this option is optional and not all data formats support this
                    }
                    // configure the rest of the options
                    configureDataFormat(dataFormat, camelContext);
                } else {
                    throw new IllegalArgumentException(
                            "Data format '" + (definition.getDataFormatName() != null ? definition.getDataFormatName() : "<null>") + "' could not be created. "
                                    + "Ensure that the data format is valid and the associated Camel component is present on the classpath");
                }
            } finally {
                propertyPlaceholdersChangeReverter.run();
            }
        }
        return dataFormat;
    }

    /**
     * Factory method to create the data format instance
     */
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        // must use getDataFormatName() as we need special logic in json dataformat
        if (definition.getDataFormatName() != null) {
            return camelContext.createDataFormat(definition.getDataFormatName());
        }
        return null;
    }

    /**
     * Allows derived classes to customize the data format
     */
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
    }

    /**
     * Sets a named property on the data format instance using introspection
     */
    protected void setProperty(CamelContext camelContext, Object bean, String name, Object value) {
        try {
            String ref = value instanceof String ? value.toString() : null;
            if (isReferenceParameter(ref) && camelContext != null) {
                IntrospectionSupport.setProperty(camelContext, camelContext.getTypeConverter(), bean, name, null, ref, true);
            } else {
                IntrospectionSupport.setProperty(camelContext, bean, name, value);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to set property: " + name + " on: " + bean + ". Reason: " + e, e);
        }
    }

}

