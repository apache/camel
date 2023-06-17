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
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.dataformat.ASN1DataFormat;
import org.apache.camel.model.dataformat.AvroDataFormat;
import org.apache.camel.model.dataformat.BarcodeDataFormat;
import org.apache.camel.model.dataformat.Base64DataFormat;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.CBORDataFormat;
import org.apache.camel.model.dataformat.ContentTypeHeaderAware;
import org.apache.camel.model.dataformat.CryptoDataFormat;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.model.dataformat.FhirDataformat;
import org.apache.camel.model.dataformat.FhirJsonDataFormat;
import org.apache.camel.model.dataformat.FhirXmlDataFormat;
import org.apache.camel.model.dataformat.FlatpackDataFormat;
import org.apache.camel.model.dataformat.GrokDataFormat;
import org.apache.camel.model.dataformat.GzipDeflaterDataFormat;
import org.apache.camel.model.dataformat.HL7DataFormat;
import org.apache.camel.model.dataformat.IcalDataFormat;
import org.apache.camel.model.dataformat.JacksonXMLDataFormat;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonApiDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.LZFDataFormat;
import org.apache.camel.model.dataformat.MimeMultipartDataFormat;
import org.apache.camel.model.dataformat.PGPDataFormat;
import org.apache.camel.model.dataformat.ParquetAvroDataFormat;
import org.apache.camel.model.dataformat.ProtobufDataFormat;
import org.apache.camel.model.dataformat.RssDataFormat;
import org.apache.camel.model.dataformat.SoapDataFormat;
import org.apache.camel.model.dataformat.SwiftMtDataFormat;
import org.apache.camel.model.dataformat.SwiftMxDataFormat;
import org.apache.camel.model.dataformat.SyslogDataFormat;
import org.apache.camel.model.dataformat.TarFileDataFormat;
import org.apache.camel.model.dataformat.ThriftDataFormat;
import org.apache.camel.model.dataformat.TidyMarkupDataFormat;
import org.apache.camel.model.dataformat.UniVocityCsvDataFormat;
import org.apache.camel.model.dataformat.UniVocityFixedDataFormat;
import org.apache.camel.model.dataformat.UniVocityTsvDataFormat;
import org.apache.camel.model.dataformat.XMLSecurityDataFormat;
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
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DataFormatReifier<T extends DataFormatDefinition> extends AbstractReifier {

    private static final Logger LOG = LoggerFactory.getLogger(DataFormatReifier.class);

    // for custom reifiers
    private static final Map<Class<? extends DataFormatDefinition>, BiFunction<CamelContext, DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>>> DATAFORMATS
            = new HashMap<>(0);

    protected final T definition;

    public DataFormatReifier(CamelContext camelContext, T definition) {
        super(camelContext);
        this.definition = definition;
    }

    public static void registerReifier(
            Class<? extends DataFormatDefinition> dataFormatClass,
            BiFunction<CamelContext, DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>> creator) {
        if (DATAFORMATS.isEmpty()) {
            ReifierStrategy.addReifierClearer(DataFormatReifier::clearReifiers);
        }
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
     * @param  camelContext the camel context
     * @param  type         the data format type
     * @param  ref          reference to lookup for a data format
     * @return              the data format or null if not possible to create
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
            type = camelContext.getCamelContextExtension().getContextPlugin(Model.class).resolveDataFormatDefinition(ref);

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

    public static DataFormatReifier<? extends DataFormatDefinition> reifier(
            CamelContext camelContext, DataFormatDefinition definition) {

        DataFormatReifier<? extends DataFormatDefinition> answer = null;
        if (!DATAFORMATS.isEmpty()) {
            // custom take precedence
            BiFunction<CamelContext, DataFormatDefinition, DataFormatReifier<? extends DataFormatDefinition>> reifier
                    = DATAFORMATS.get(definition.getClass());
            if (reifier != null) {
                answer = reifier.apply(camelContext, definition);
            }
        }
        if (answer == null) {
            answer = coreReifier(camelContext, definition);
        }
        if (answer == null) {
            throw new IllegalStateException("Unsupported definition: " + definition);
        }
        return answer;
    }

    private static DataFormatReifier<? extends DataFormatDefinition> coreReifier(
            CamelContext camelContext, DataFormatDefinition definition) {
        if (definition instanceof ASN1DataFormat) {
            return new ASN1DataFormatReifier(camelContext, definition);
        } else if (definition instanceof AvroDataFormat) {
            return new AvroDataFormatReifier(camelContext, definition);
        } else if (definition instanceof BarcodeDataFormat) {
            return new BarcodeDataFormatReifier(camelContext, definition);
        } else if (definition instanceof Base64DataFormat) {
            return new Base64DataFormatReifier(camelContext, definition);
        } else if (definition instanceof BindyDataFormat) {
            return new BindyDataFormatReifier(camelContext, definition);
        } else if (definition instanceof CBORDataFormat) {
            return new CBORDataFormatReifier(camelContext, definition);
        } else if (definition instanceof CryptoDataFormat) {
            return new CryptoDataFormatReifier(camelContext, definition);
        } else if (definition instanceof CsvDataFormat) {
            return new CsvDataFormatReifier(camelContext, definition);
        } else if (definition instanceof CustomDataFormat) {
            return new CustomDataFormatReifier(camelContext, definition);
        } else if (definition instanceof FhirJsonDataFormat) {
            return new FhirJsonDataFormatReifier(camelContext, definition);
        } else if (definition instanceof FhirXmlDataFormat) {
            return new FhirXmlDataFormatReifier(camelContext, definition);
        } else if (definition instanceof FhirDataformat) {
            return new FhirDataFormatReifier<>(camelContext, definition);
        } else if (definition instanceof FlatpackDataFormat) {
            return new FlatpackDataFormatReifier(camelContext, definition);
        } else if (definition instanceof GrokDataFormat) {
            return new GrokDataFormatReifier(camelContext, definition);
        } else if (definition instanceof GzipDeflaterDataFormat) {
            return new GzipDataFormatReifier(camelContext, definition);
        } else if (definition instanceof HL7DataFormat) {
            return new HL7DataFormatReifier(camelContext, definition);
        } else if (definition instanceof IcalDataFormat) {
            return new IcalDataFormatReifier(camelContext, definition);
        } else if (definition instanceof JacksonXMLDataFormat) {
            return new JacksonXMLDataFormatReifier(camelContext, definition);
        } else if (definition instanceof JaxbDataFormat) {
            return new JaxbDataFormatReifier(camelContext, definition);
        } else if (definition instanceof JsonApiDataFormat) {
            return new JsonApiDataFormatReifier(camelContext, definition);
        } else if (definition instanceof JsonDataFormat) {
            return new JsonDataFormatReifier(camelContext, definition);
        } else if (definition instanceof LZFDataFormat) {
            return new LZFDataFormatReifier(camelContext, definition);
        } else if (definition instanceof MimeMultipartDataFormat) {
            return new MimeMultipartDataFormatReifier(camelContext, definition);
        } else if (definition instanceof ParquetAvroDataFormat) {
            return new ParquetAvroDataFormatReifier(camelContext, definition);
        } else if (definition instanceof PGPDataFormat) {
            return new PGPDataFormatReifier(camelContext, definition);
        } else if (definition instanceof ProtobufDataFormat) {
            return new ProtobufDataFormatReifier(camelContext, definition);
        } else if (definition instanceof RssDataFormat) {
            return new RssDataFormatReifier(camelContext, definition);
        } else if (definition instanceof SoapDataFormat) {
            return new SoapDataFormatReifier(camelContext, definition);
        } else if (definition instanceof SyslogDataFormat) {
            return new SyslogDataFormatReifier(camelContext, definition);
        } else if (definition instanceof SwiftMtDataFormat) {
            return new SwiftMtDataFormatReifier(camelContext, definition);
        } else if (definition instanceof SwiftMxDataFormat) {
            return new SwiftMxDataFormatReifier(camelContext, definition);
        } else if (definition instanceof TarFileDataFormat) {
            return new TarFileDataFormatReifier(camelContext, definition);
        } else if (definition instanceof ThriftDataFormat) {
            return new ThriftDataFormatReifier(camelContext, definition);
        } else if (definition instanceof TidyMarkupDataFormat) {
            return new TidyMarkupDataFormatReifier(camelContext, definition);
        } else if (definition instanceof UniVocityCsvDataFormat) {
            return new UniVocityCsvDataFormatReifier(camelContext, definition);
        } else if (definition instanceof UniVocityFixedDataFormat) {
            return new UniVocityFixedWidthDataFormatReifier(camelContext, definition);
        } else if (definition instanceof UniVocityTsvDataFormat) {
            return new UniVocityTsvDataFormatReifier(camelContext, definition);
        } else if (definition instanceof XMLSecurityDataFormat) {
            return new XMLSecurityDataFormatReifier(camelContext, definition);
        } else if (definition instanceof YAMLDataFormat) {
            return new YAMLDataFormatReifier(camelContext, definition);
        } else if (definition instanceof ZipDeflaterDataFormat) {
            return new ZipDataFormatReifier(camelContext, definition);
        } else if (definition instanceof ZipFileDataFormat) {
            return new ZipFileDataFormatReifier(camelContext, definition);
        }
        return null;
    }

    public DataFormat createDataFormat() {
        DataFormat dataFormat = definition.getDataFormat();
        if (dataFormat == null) {
            dataFormat = doCreateDataFormat();
            if (dataFormat != null) {
                if (dataFormat instanceof DataFormatContentTypeHeader && definition instanceof ContentTypeHeaderAware) {
                    String header = ((ContentTypeHeaderAware) definition).getContentTypeHeader();
                    // is enabled by default so assume true if null
                    final boolean contentTypeHeader = parseBoolean(header, true);
                    ((DataFormatContentTypeHeader) dataFormat).setContentTypeHeader(contentTypeHeader);
                }
                // configure the rest of the options
                configureDataFormat(dataFormat);
            } else {
                throw new IllegalArgumentException(
                        "Data format '" + (definition.getDataFormatName() != null ? definition.getDataFormatName() : "<null>")
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
                .withIgnoreCase(true)
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
                LOG.debug("Discovered dataformat property configurer using the PropertyConfigurerAware: {} -> {}", name,
                        configurer);
            }
        }
        if (configurer == null) {
            String configurerName = name + "-dataformat-configurer";
            configurer = PluginHelper.getConfigurerResolver(camelContext)
                    .resolvePropertyConfigurer(configurerName, camelContext);
        }
        return configurer;
    }

    protected abstract void prepareDataFormatConfig(Map<String, Object> properties);

    protected String asTypeName(Class<?> classType) {
        String type;
        if (!classType.isPrimitive()) {
            if (classType.isArray()) {
                type = StringHelper.between(classType.getName(), "[L", ";") + "[]";
            } else {
                type = classType.getName();
            }
        } else {
            type = classType.getCanonicalName();
        }

        return type;
    }

}
