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
package org.apache.camel.builder;

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

/**
 * {@code DataFormatBuilderFactory} is a factory class of builder of all supported data formats.
 */
public final class DataFormatBuilderFactory {

    /**
     * Uses the ASN.1 file data format
     */
    public ASN1DataFormat.Builder asn1() {
        return new ASN1DataFormat.Builder();
    }

    /**
     * Uses the Avro data format
     */
    public AvroDataFormat.Builder avro() {
        return new AvroDataFormat.Builder();
    }

    /**
     * Uses the Barcode data format
     */
    public BarcodeDataFormat.Builder barcode() {
        return new BarcodeDataFormat.Builder();
    }

    /**
     * Uses the base64 data format
     */
    public Base64DataFormat.Builder base64() {
        return new Base64DataFormat.Builder();
    }

    /**
     * Uses the BeanIO data format
     */
    public BeanioDataFormat.Builder beanio() {
        return new BeanioDataFormat.Builder();
    }

    /**
     * Uses the Bindy data format
     */
    public BindyDataFormat.Builder bindy() {
        return new BindyDataFormat.Builder();
    }

    /**
     * Uses the CBOR data format
     */
    public CBORDataFormat.Builder cbor() {
        return new CBORDataFormat.Builder();
    }

    /**
     * Uses the Crypto data format
     */
    public CryptoDataFormat.Builder crypto() {
        return new CryptoDataFormat.Builder();
    }

    /**
     * Uses the CSV data format
     */
    public CsvDataFormat.Builder csv() {
        return new CsvDataFormat.Builder();
    }

    /**
     * Uses the custom data format
     */
    public CustomDataFormat.Builder custom() {
        return new CustomDataFormat.Builder();
    }

    /**
     * Uses the FHIR JSON data format
     */
    public FhirJsonDataFormat.Builder fhirJson() {
        return new FhirJsonDataFormat.Builder();
    }

    /**
     * Uses the FHIR XML data format
     */
    public FhirXmlDataFormat.Builder fhirXml() {
        return new FhirXmlDataFormat.Builder();
    }

    /**
     * Uses the Flatpack data format
     */
    public FlatpackDataFormat.Builder flatpack() {
        return new FlatpackDataFormat.Builder();
    }

    /**
     * Uses the Grok data format
     */
    public GrokDataFormat.Builder grok() {
        return new GrokDataFormat.Builder();
    }

    /**
     * Uses the GZIP deflater data format
     */
    public GzipDeflaterDataFormat.Builder gzipDeflater() {
        return new GzipDeflaterDataFormat.Builder();
    }

    /**
     * Uses the HL7 data format
     */
    public HL7DataFormat.Builder hl7() {
        return new HL7DataFormat.Builder();
    }

    /**
     * Uses the iCal data format
     */
    public IcalDataFormat.Builder ical() {
        return new IcalDataFormat.Builder();
    }

    /**
     * Uses the Jackson XML data format
     */
    public JacksonXMLDataFormat.Builder jacksonXml() {
        return new JacksonXMLDataFormat.Builder();
    }

    /**
     * Uses the JAXB data format
     */
    public JaxbDataFormat.Builder jaxb() {
        return new JaxbDataFormat.Builder();
    }

    /**
     * Uses the JSON API data format
     */
    public JsonApiDataFormat.Builder jsonApi() {
        return new JsonApiDataFormat.Builder();
    }

    /**
     * Uses the JSON data format using the Jackson library
     */
    public JsonDataFormat.Builder json() {
        return new JsonDataFormat.Builder();
    }

    /**
     * Uses the LZF deflater data format
     */
    public LZFDataFormat.Builder lzf() {
        return new LZFDataFormat.Builder();
    }

    /**
     * Uses the MIME Multipart data format
     */
    public MimeMultipartDataFormat.Builder mimeMultipart() {
        return new MimeMultipartDataFormat.Builder();
    }

    /**
     * Uses the protobuf data format
     */
    public ParquetAvroDataFormat.Builder parquetAvro() {
        return new ParquetAvroDataFormat.Builder();
    }

    /**
     * Uses the PGP data format
     */
    public PGPDataFormat.Builder pgp() {
        return new PGPDataFormat.Builder();
    }

    /**
     * Uses the protobuf data format
     */
    public ProtobufDataFormat.Builder protobuf() {
        return new ProtobufDataFormat.Builder();
    }

    /**
     * Uses the RSS data format
     */
    public RssDataFormat.Builder rss() {
        return new RssDataFormat.Builder();
    }

    /**
     * Uses the Soap v1.1 data format
     */
    public SoapDataFormat.Builder soap() {
        return new SoapDataFormat.Builder();
    }

    /**
     * Uses the SWIFT MX data format
     */
    public SwiftMxDataFormat.Builder swiftMx() {
        return new SwiftMxDataFormat.Builder();
    }

    /**
     * Uses the SWIFT MT data format
     */
    public SwiftMtDataFormat.Builder swiftMt() {
        return new SwiftMtDataFormat.Builder();
    }

    /**
     * Uses the Syslog data format
     */
    public SyslogDataFormat.Builder syslog() {
        return new SyslogDataFormat.Builder();
    }

    /**
     * Uses the Tar file data format
     */
    public TarFileDataFormat.Builder tarFile() {
        return new TarFileDataFormat.Builder();
    }

    /**
     * Uses the Thrift data format
     */
    public ThriftDataFormat.Builder thrift() {
        return new ThriftDataFormat.Builder();
    }

    /**
     * Return TidyMarkup in the default format as {@link org.w3c.dom.Node}
     */
    public TidyMarkupDataFormat.Builder tidyMarkup() {
        return new TidyMarkupDataFormat.Builder();
    }

    /**
     * Uses the UniVosity CSV data format
     */
    public UniVocityCsvDataFormat.Builder univocityCsv() {
        return new UniVocityCsvDataFormat.Builder();
    }

    /**
     * Uses the UniVosity Fixed data format
     */
    public UniVocityFixedDataFormat.Builder univocityFixed() {
        return new UniVocityFixedDataFormat.Builder();
    }

    public UniVocityTsvDataFormat.Builder univocityTsv() {
        return new UniVocityTsvDataFormat.Builder();
    }

    /**
     * Uses the XML Security data format
     */
    public XMLSecurityDataFormat.Builder xmlSecurity() {
        return new XMLSecurityDataFormat.Builder();
    }

    /**
     * Uses the YAML data format
     */
    public YAMLDataFormat.Builder yaml() {
        return new YAMLDataFormat.Builder();
    }

    /**
     * Uses the ZIP deflater data format
     */
    public ZipDeflaterDataFormat.Builder zipDeflater() {
        return new ZipDeflaterDataFormat.Builder();
    }

    /**
     * Uses the ZIP file data format
     */
    public ZipFileDataFormat.Builder zipFile() {
        return new ZipFileDataFormat.Builder();
    }
}
