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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

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
import org.apache.camel.spi.Metadata;

/**
 * Marshals data into a specified format for transmission over a transport or
 * component
 */
@Metadata(label = "dataformat,transformation")
@XmlRootElement(name = "marshal")
@XmlAccessorType(XmlAccessType.FIELD)
public class MarshalDefinition extends NoOutputDefinition<MarshalDefinition> {

    @XmlElements({@XmlElement(required = false, name = "any23", type = Any23DataFormat.class),
            @XmlElement(required = false, name = "asn1", type = ASN1DataFormat.class),
            @XmlElement(required = false, name = "avro", type = AvroDataFormat.class),
            @XmlElement(required = false, name = "barcode", type = BarcodeDataFormat.class),
            @XmlElement(required = false, name = "base64", type = Base64DataFormat.class),
            @XmlElement(required = false, name = "beanio", type = BeanioDataFormat.class),
            @XmlElement(required = false, name = "bindy", type = BindyDataFormat.class),
            @XmlElement(required = false, name = "cbor", type = CBORDataFormat.class),
            @XmlElement(required = false, name = "crypto", type = CryptoDataFormat.class),
            @XmlElement(required = false, name = "csv", type = CsvDataFormat.class),
            @XmlElement(required = false, name = "custom", type = CustomDataFormat.class),
            @XmlElement(required = false, name = "fhirJson", type = FhirJsonDataFormat.class),
            @XmlElement(required = false, name = "fhirXml", type = FhirXmlDataFormat.class),
            @XmlElement(required = false, name = "flatpack", type = FlatpackDataFormat.class),
            @XmlElement(required = false, name = "grok", type = GrokDataFormat.class),
            @XmlElement(required = false, name = "gzip", type = GzipDataFormat.class),
            @XmlElement(required = false, name = "hl7", type = HL7DataFormat.class),
            @XmlElement(required = false, name = "ical", type = IcalDataFormat.class),
            @XmlElement(required = false, name = "jacksonxml", type = JacksonXMLDataFormat.class),
            @XmlElement(required = false, name = "jaxb", type = JaxbDataFormat.class),
            @XmlElement(required = false, name = "json", type = JsonDataFormat.class),
            @XmlElement(required = false, name = "jsonApi", type = JsonApiDataFormat.class),
            @XmlElement(required = false, name = "lzf", type = LZFDataFormat.class),
            @XmlElement(required = false, name = "mimeMultipart", type = MimeMultipartDataFormat.class),
            @XmlElement(required = false, name = "protobuf", type = ProtobufDataFormat.class),
            @XmlElement(required = false, name = "rss", type = RssDataFormat.class),
            @XmlElement(required = false, name = "secureXML", type = XMLSecurityDataFormat.class),
            @XmlElement(required = false, name = "soapjaxb", type = SoapJaxbDataFormat.class),
            @XmlElement(required = false, name = "syslog", type = SyslogDataFormat.class),
            @XmlElement(required = false, name = "tarfile", type = TarFileDataFormat.class),
            @XmlElement(required = false, name = "thrift", type = ThriftDataFormat.class),
            @XmlElement(required = false, name = "tidyMarkup", type = TidyMarkupDataFormat.class),
            @XmlElement(required = false, name = "univocity-csv", type = UniVocityCsvDataFormat.class),
            @XmlElement(required = false, name = "univocity-fixed", type = UniVocityFixedWidthDataFormat.class),
            @XmlElement(required = false, name = "univocity-tsv", type = UniVocityTsvDataFormat.class),
            @XmlElement(required = false, name = "xmlrpc", type = XmlRpcDataFormat.class),
            @XmlElement(required = false, name = "xstream", type = XStreamDataFormat.class),
            @XmlElement(required = false, name = "pgp", type = PGPDataFormat.class),
            @XmlElement(required = false, name = "yaml", type = YAMLDataFormat.class),
            @XmlElement(required = false, name = "zip", type = ZipDeflaterDataFormat.class),
            @XmlElement(required = false, name = "zipfile", type = ZipFileDataFormat.class)})
    private DataFormatDefinition dataFormatType;

    public MarshalDefinition() {
    }

    public MarshalDefinition(DataFormatDefinition dataFormatType) {
        this.dataFormatType = dataFormatType;
    }

    @Override
    public String toString() {
        return "Marshal[" + description() + "]";
    }

    protected String description() {
        return dataFormatType != null ? dataFormatType.toString() : "";
    }

    @Override
    public String getShortName() {
        return "marshal";
    }

    @Override
    public String getLabel() {
        return "marshal[" + description() + "]";
    }

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
