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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Configure data formats.
 */
@Metadata(label = "dataformat,transformation", title = "Data formats")
@XmlRootElement(name = "dataFormats")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataFormatsDefinition {

    // cannot use @XmlElementRef as it doesn't allow optional properties
    @XmlElements({
            @XmlElement(name = "asn1", type = ASN1DataFormat.class),
            @XmlElement(name = "avro", type = AvroDataFormat.class),
            @XmlElement(name = "barcode", type = BarcodeDataFormat.class),
            @XmlElement(name = "base64", type = Base64DataFormat.class),
            @XmlElement(name = "beanio", type = BeanioDataFormat.class),
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
            @XmlElement(name = "parquetAvro", type = ParquetAvroDataFormat.class),
            @XmlElement(name = "pgp", type = PGPDataFormat.class),
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
            @XmlElement(name = "yaml", type = YAMLDataFormat.class),
            @XmlElement(name = "zipDeflater", type = ZipDeflaterDataFormat.class),
            @XmlElement(name = "zipFile", type = ZipFileDataFormat.class) })
    private List<DataFormatDefinition> dataFormats;

    /**
     * A list holding the configured data formats
     */
    public void setDataFormats(List<DataFormatDefinition> dataFormats) {
        this.dataFormats = dataFormats;
    }

    public List<DataFormatDefinition> getDataFormats() {
        return dataFormats;
    }

    /***
     * @return A Map of the contained DataFormatType's indexed by id.
     */
    public Map<String, DataFormatDefinition> asMap() {
        Map<String, DataFormatDefinition> dataFormatsAsMap = new HashMap<>();
        for (DataFormatDefinition dataFormatType : getDataFormats()) {
            dataFormatsAsMap.put(dataFormatType.getId(), dataFormatType);
        }
        return dataFormatsAsMap;
    }
}
