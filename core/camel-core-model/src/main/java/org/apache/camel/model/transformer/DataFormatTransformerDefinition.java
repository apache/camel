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
package org.apache.camel.model.transformer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.ASN1DataFormat;
import org.apache.camel.model.dataformat.Any23DataFormat;
import org.apache.camel.model.dataformat.AvroDataFormat;
import org.apache.camel.model.dataformat.BarcodeDataFormat;
import org.apache.camel.model.dataformat.Base64DataFormat;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.CBORDataFormat;
import org.apache.camel.model.dataformat.CryptoDataFormat;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.CustomDataFormat;
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
import org.apache.camel.model.dataformat.XStreamDataFormat;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.ZipDeflaterDataFormat;
import org.apache.camel.model.dataformat.ZipFileDataFormat;
import org.apache.camel.spi.Metadata;

/**
 * Represents a {@link org.apache.camel.processor.transformer.DataFormatTransformer} which leverages
 * {@link org.apache.camel.spi.DataFormat} to perform transformation. One of the DataFormat 'ref' or DataFormat 'type'
 * needs to be specified.
 */
@Metadata(label = "dataformat,transformation")
@XmlType(name = "dataFormatTransformer")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataFormatTransformerDefinition extends TransformerDefinition {

    @XmlElements({
            @XmlElement(name = "any23", type = Any23DataFormat.class),
            @XmlElement(name = "asn1", type = ASN1DataFormat.class),
            @XmlElement(name = "avro", type = AvroDataFormat.class),
            @XmlElement(name = "barcode", type = BarcodeDataFormat.class),
            @XmlElement(name = "base64", type = Base64DataFormat.class),
            @XmlElement(name = "bindy", type = BindyDataFormat.class),
            @XmlElement(name = "cbor", type = CBORDataFormat.class),
            @XmlElement(name = "crypto", type = CryptoDataFormat.class),
            @XmlElement(name = "csv", type = CsvDataFormat.class),
            @XmlElement(name = "custom", type = CustomDataFormat.class),
            @XmlElement(name = "fhirJson", type = FhirJsonDataFormat.class),
            @XmlElement(name = "fhirXml", type = FhirXmlDataFormat.class),
            @XmlElement(name = "flatpack", type = FlatpackDataFormat.class),
            @XmlElement(name = "grok", type = GrokDataFormat.class),
            @XmlElement(name = "gzipDeflater", type = GzipDeflaterDataFormat.class),
            @XmlElement(name = "hl7", type = HL7DataFormat.class),
            @XmlElement(name = "ical", type = IcalDataFormat.class),
            @XmlElement(name = "jacksonXml", type = JacksonXMLDataFormat.class),
            @XmlElement(name = "jaxb", type = JaxbDataFormat.class),
            @XmlElement(name = "json", type = JsonDataFormat.class),
            @XmlElement(name = "jsonApi", type = JsonApiDataFormat.class),
            @XmlElement(name = "lzf", type = LZFDataFormat.class),
            @XmlElement(name = "mimeMultipart", type = MimeMultipartDataFormat.class),
            @XmlElement(name = "protobuf", type = ProtobufDataFormat.class),
            @XmlElement(name = "rss", type = RssDataFormat.class),
            @XmlElement(name = "soap", type = SoapDataFormat.class),
            @XmlElement(name = "swiftMt", type = SwiftMtDataFormat.class),
            @XmlElement(name = "swiftMx", type = SwiftMxDataFormat.class),
            @XmlElement(name = "syslog", type = SyslogDataFormat.class),
            @XmlElement(name = "tarFile", type = TarFileDataFormat.class),
            @XmlElement(name = "thrift", type = ThriftDataFormat.class),
            @XmlElement(name = "tidyMarkup", type = TidyMarkupDataFormat.class),
            @XmlElement(name = "univocityCsv", type = UniVocityCsvDataFormat.class),
            @XmlElement(name = "univocityFixed", type = UniVocityFixedDataFormat.class),
            @XmlElement(name = "univocityTsv", type = UniVocityTsvDataFormat.class),
            @XmlElement(name = "xmlSecurity", type = XMLSecurityDataFormat.class),
            @XmlElement(name = "xstream", type = XStreamDataFormat.class),
            @XmlElement(name = "pgp", type = PGPDataFormat.class),
            @XmlElement(name = "yaml", type = YAMLDataFormat.class),
            @XmlElement(name = "zipDeflater", type = ZipDeflaterDataFormat.class),
            @XmlElement(name = "zipFile", type = ZipFileDataFormat.class) })
    private DataFormatDefinition dataFormatType;

    public DataFormatDefinition getDataFormatType() {
        return dataFormatType;
    }

    /**
     * The data format to be used
     */
    public void setDataFormatType(DataFormatDefinition dataFormatType) {
        this.dataFormatType = dataFormatType;
    }

}
