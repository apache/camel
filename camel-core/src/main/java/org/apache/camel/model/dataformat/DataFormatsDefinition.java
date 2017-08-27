/**
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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * To configure data formats
 */
@Metadata(label = "dataformat,transformation", title = "Data formats")
@XmlRootElement(name = "dataFormats")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataFormatsDefinition {

    // cannot use @XmlElementRef as it doesn't allow optional properties
    @XmlElements({
        @XmlElement(required = false, name = "asn1", type = ASN1DataFormat.class),
        @XmlElement(required = false, name = "avro", type = AvroDataFormat.class),
        @XmlElement(required = false, name = "barcode", type = BarcodeDataFormat.class),
        @XmlElement(required = false, name = "base64", type = Base64DataFormat.class),
        @XmlElement(required = false, name = "beanio", type = BeanioDataFormat.class),
        @XmlElement(required = false, name = "bindy", type = BindyDataFormat.class),
        @XmlElement(required = false, name = "boon", type = BoonDataFormat.class),
        @XmlElement(required = false, name = "castor", type = CastorDataFormat.class),
        @XmlElement(required = false, name = "crypto", type = CryptoDataFormat.class),
        @XmlElement(required = false, name = "csv", type = CsvDataFormat.class),
        // TODO: Camel 3.0 - Should be named customDataFormat to avoid naming clash with custom loadbalancer
        @XmlElement(required = false, name = "custom", type = CustomDataFormat.class),
        @XmlElement(required = false, name = "flatpack", type = FlatpackDataFormat.class),
        @XmlElement(required = false, name = "gzip", type = GzipDataFormat.class),
        @XmlElement(required = false, name = "hessian", type = HessianDataFormat.class),
        @XmlElement(required = false, name = "hl7", type = HL7DataFormat.class),
        @XmlElement(required = false, name = "ical", type = IcalDataFormat.class),
        @XmlElement(required = false, name = "jacksonxml", type = JacksonXMLDataFormat.class),
        @XmlElement(required = false, name = "jaxb", type = JaxbDataFormat.class),
        @XmlElement(required = false, name = "jibx", type = JibxDataFormat.class),
        @XmlElement(required = false, name = "json", type = JsonDataFormat.class),
        @XmlElement(required = false, name = "lzf", type = LZFDataFormat.class),
        @XmlElement(required = false, name = "mimeMultipart", type = MimeMultipartDataFormat.class),
        @XmlElement(required = false, name = "protobuf", type = ProtobufDataFormat.class),
        @XmlElement(required = false, name = "rss", type = RssDataFormat.class),
        @XmlElement(required = false, name = "secureXML", type = XMLSecurityDataFormat.class),
        @XmlElement(required = false, name = "serialization", type = SerializationDataFormat.class),
        @XmlElement(required = false, name = "soapjaxb", type = SoapJaxbDataFormat.class),
        @XmlElement(required = false, name = "string", type = StringDataFormat.class),
        @XmlElement(required = false, name = "syslog", type = SyslogDataFormat.class),
        @XmlElement(required = false, name = "tarfile", type = TarFileDataFormat.class),
        @XmlElement(required = false, name = "thrift", type = ThriftDataFormat.class),
        @XmlElement(required = false, name = "tidyMarkup", type = TidyMarkupDataFormat.class),
        @XmlElement(required = false, name = "univocity-csv", type = UniVocityCsvDataFormat.class),
        @XmlElement(required = false, name = "univocity-fixed", type = UniVocityFixedWidthDataFormat.class),
        @XmlElement(required = false, name = "univocity-tsv", type = UniVocityTsvDataFormat.class),
        @XmlElement(required = false, name = "xmlBeans", type = XMLBeansDataFormat.class),
        @XmlElement(required = false, name = "xmljson", type = XmlJsonDataFormat.class),
        @XmlElement(required = false, name = "xmlrpc", type = XmlRpcDataFormat.class),
        @XmlElement(required = false, name = "xstream", type = XStreamDataFormat.class),
        @XmlElement(required = false, name = "pgp", type = PGPDataFormat.class),
        @XmlElement(required = false, name = "yaml", type = YAMLDataFormat.class),
        @XmlElement(required = false, name = "zip", type = ZipDataFormat.class),
        @XmlElement(required = false, name = "zipFile", type = ZipFileDataFormat.class)}
        )
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
        Map<String, DataFormatDefinition> dataFormatsAsMap = new HashMap<String, DataFormatDefinition>();
        for (DataFormatDefinition dataFormatType : getDataFormats()) {
            dataFormatsAsMap.put(dataFormatType.getId(), dataFormatType);
        }
        return dataFormatsAsMap;
    }
}
