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

import java.util.Map;

import org.w3c.dom.Node;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.dataformat.ASN1DataFormat;
import org.apache.camel.model.dataformat.AvroDataFormat;
import org.apache.camel.model.dataformat.AvroLibrary;
import org.apache.camel.model.dataformat.Base64DataFormat;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.model.dataformat.CBORDataFormat;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.model.dataformat.FhirJsonDataFormat;
import org.apache.camel.model.dataformat.FhirXmlDataFormat;
import org.apache.camel.model.dataformat.GrokDataFormat;
import org.apache.camel.model.dataformat.GzipDeflaterDataFormat;
import org.apache.camel.model.dataformat.HL7DataFormat;
import org.apache.camel.model.dataformat.IcalDataFormat;
import org.apache.camel.model.dataformat.JacksonXMLDataFormat;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonApiDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.LZFDataFormat;
import org.apache.camel.model.dataformat.MimeMultipartDataFormat;
import org.apache.camel.model.dataformat.PGPDataFormat;
import org.apache.camel.model.dataformat.ParquetAvroDataFormat;
import org.apache.camel.model.dataformat.ProtobufDataFormat;
import org.apache.camel.model.dataformat.ProtobufLibrary;
import org.apache.camel.model.dataformat.RssDataFormat;
import org.apache.camel.model.dataformat.SoapDataFormat;
import org.apache.camel.model.dataformat.SwiftMtDataFormat;
import org.apache.camel.model.dataformat.SwiftMxDataFormat;
import org.apache.camel.model.dataformat.SyslogDataFormat;
import org.apache.camel.model.dataformat.TarFileDataFormat;
import org.apache.camel.model.dataformat.ThriftDataFormat;
import org.apache.camel.model.dataformat.TidyMarkupDataFormat;
import org.apache.camel.model.dataformat.XMLSecurityDataFormat;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.apache.camel.model.dataformat.ZipDeflaterDataFormat;
import org.apache.camel.model.dataformat.ZipFileDataFormat;
import org.apache.camel.support.jsse.KeyStoreParameters;

/**
 * An expression for constructing the different possible {@link org.apache.camel.spi.DataFormat} options.
 */
public class DataFormatClause<T extends ProcessorDefinition<?>> {
    private final T processorType;
    private final Operation operation;
    private boolean allowNullBody;

    /**
     * {@link org.apache.camel.spi.DataFormat} operations.
     */
    public enum Operation {
        Marshal,
        Unmarshal
    }

    public DataFormatClause(T processorType, Operation operation) {
        this.processorType = processorType;
        this.operation = operation;
    }

    /**
     * Uses the Avro data format
     */
    public T avro() {
        return dataFormat(new AvroDataFormat());
    }

    public T avro(Object schema) {
        AvroDataFormat dataFormat = new AvroDataFormat();
        dataFormat.setSchema(schema);
        return dataFormat(dataFormat);
    }

    public T avro(String instanceClassName) {
        return dataFormat(new AvroDataFormat(instanceClassName));
    }

    /**
     * Uses Avro data format with library
     */
    public T avro(AvroLibrary library) {
        return dataFormat(new AvroDataFormat(library));
    }

    /**
     * Uses the Avro data format with given unmarshalType
     */
    public T avro(Class<?> unmarshalType) {
        AvroDataFormat avroDataFormat = new AvroDataFormat();
        avroDataFormat.setUnmarshalType(unmarshalType);
        return dataFormat(avroDataFormat);
    }

    /**
     * Uses the Avro data format with given library and unmarshalType
     */
    public T avro(AvroLibrary library, Class<?> unmarshalType) {
        AvroDataFormat avroDataFormat = new AvroDataFormat();
        avroDataFormat.setLibrary(library);
        avroDataFormat.setUnmarshalType(unmarshalType);
        return dataFormat(avroDataFormat);
    }

    /**
     * Uses the Avro data format with given library, unmarshalType and schemaResolver
     */
    public T avro(AvroLibrary library, Class<?> unmarshalType, String schemaResolver) {
        AvroDataFormat avroDataFormat = new AvroDataFormat();
        avroDataFormat.setLibrary(library);
        avroDataFormat.setUnmarshalType(unmarshalType);
        avroDataFormat.setSchemaResolver(schemaResolver);
        return dataFormat(avroDataFormat);
    }

    /**
     * Uses the base64 data format
     */
    public T base64() {
        Base64DataFormat dataFormat = new Base64DataFormat();
        return dataFormat(dataFormat);
    }

    /**
     * Uses the base64 data format
     */
    public T base64(int lineLength, String lineSeparator, boolean urlSafe) {
        Base64DataFormat dataFormat = new Base64DataFormat();
        dataFormat.setLineLength(Integer.toString(lineLength));
        dataFormat.setLineSeparator(lineSeparator);
        dataFormat.setUrlSafe(Boolean.toString(urlSafe));
        return dataFormat(dataFormat);
    }

    /**
     * Uses the Bindy data format
     *
     * @param type      the type of bindy data format to use
     * @param classType the POJO class type
     */
    public T bindy(BindyType type, Class<?> classType) {
        BindyDataFormat bindy = new BindyDataFormat();
        bindy.setType(type.name());
        bindy.setClassType(classType);
        return dataFormat(bindy);
    }

    /**
     * Uses the Bindy data format
     *
     * @param type                 the type of bindy data format to use
     * @param classType            the POJO class type
     * @param unwrapSingleInstance whether unmarshal should unwrap if there is a single instance in the result
     */
    public T bindy(BindyType type, Class<?> classType, boolean unwrapSingleInstance) {
        BindyDataFormat bindy = new BindyDataFormat();
        bindy.setType(type.name());
        bindy.setClassType(classType);
        bindy.setUnwrapSingleInstance(Boolean.toString(unwrapSingleInstance));
        return dataFormat(bindy);
    }

    /**
     * Uses the CBOR data format
     */
    public T cbor() {
        return dataFormat(new CBORDataFormat());
    }

    /**
     * Uses the CBOR data format
     *
     * @param unmarshalType unmarshal type for cbor type
     */
    public T cbor(Class<?> unmarshalType) {
        CBORDataFormat cborDataFormat = new CBORDataFormat();
        cborDataFormat.setUnmarshalType(unmarshalType);
        return dataFormat(cborDataFormat);
    }

    /**
     * Uses the CSV data format
     */
    public T csv() {
        return dataFormat(new CsvDataFormat());
    }

    /**
     * Uses the CSV data format for a huge file. Sequential access through an iterator.
     */
    public T csvLazyLoad() {
        return dataFormat(new CsvDataFormat(true));
    }

    /**
     * Uses the custom data format
     */
    public T custom(String ref) {
        return dataFormat(new CustomDataFormat(ref));
    }

    /**
     * Uses the Grok data format
     */
    public T grok(String pattern) {
        GrokDataFormat grokDataFormat = new GrokDataFormat();
        grokDataFormat.setPattern(pattern);
        return dataFormat(grokDataFormat);
    }

    /**
     * Uses the GZIP deflater data format
     */
    public T gzipDeflater() {
        GzipDeflaterDataFormat gzdf = new GzipDeflaterDataFormat();
        return dataFormat(gzdf);
    }

    /**
     * Uses the HL7 data format
     */
    public T hl7() {
        return dataFormat(new HL7DataFormat());
    }

    /**
     * Uses the HL7 data format
     */
    public T hl7(boolean validate) {
        HL7DataFormat hl7 = new HL7DataFormat();
        hl7.setValidate(Boolean.toString(validate));
        return dataFormat(hl7);
    }

    /**
     * Uses the HL7 data format
     */
    public T hl7(Object parser) {
        HL7DataFormat hl7 = new HL7DataFormat();
        hl7.setParser(parser);
        return dataFormat(hl7);
    }

    /**
     * Uses the iCal data format
     */
    public T ical(boolean validating) {
        IcalDataFormat ical = new IcalDataFormat();
        ical.setValidating(Boolean.toString(validating));
        return dataFormat(ical);
    }

    /**
     * Uses the LZF deflater data format
     */
    public T lzf() {
        LZFDataFormat lzfdf = new LZFDataFormat();
        return dataFormat(lzfdf);
    }

    /**
     * Uses the MIME Multipart data format
     */
    public T mimeMultipart() {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        return dataFormat(mm);
    }

    /**
     * Uses the MIME Multipart data format
     *
     * @param multipartSubType Specifies the subtype of the MIME Multipart
     */
    public T mimeMultipart(String multipartSubType) {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        mm.setMultipartSubType(multipartSubType);
        return dataFormat(mm);
    }

    /**
     * Uses the MIME Multipart data format
     *
     * @param multipartSubType           the subtype of the MIME Multipart
     * @param multipartWithoutAttachment defines whether a message without attachment is also marshaled into a MIME
     *                                   Multipart (with only one body part).
     * @param headersInline              define the MIME Multipart headers as part of the message body or as Camel
     *                                   headers
     * @param binaryContent              have binary encoding for binary content (true) or use Base-64 encoding for
     *                                   binary content (false)
     */
    public T mimeMultipart(
            String multipartSubType, boolean multipartWithoutAttachment, boolean headersInline, boolean binaryContent) {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        mm.setMultipartSubType(multipartSubType);
        mm.setMultipartWithoutAttachment(Boolean.toString(multipartWithoutAttachment));
        mm.setHeadersInline(Boolean.toString(headersInline));
        mm.setBinaryContent(Boolean.toString(binaryContent));
        return dataFormat(mm);
    }

    /**
     * Uses the MIME Multipart data format
     *
     * @param multipartSubType           the subtype of the MIME Multipart
     * @param multipartWithoutAttachment defines whether a message without attachment is also marshaled into a MIME
     *                                   Multipart (with only one body part).
     * @param headersInline              define the MIME Multipart headers as part of the message body or as Camel
     *                                   headers
     * @param includeHeaders             if headersInline is set to true all camel headers matching this regex are also
     *                                   stored as MIME headers on the Multipart
     * @param binaryContent              have binary encoding for binary content (true) or use Base-64 encoding for
     *                                   binary content (false)
     */
    public T mimeMultipart(
            String multipartSubType, boolean multipartWithoutAttachment, boolean headersInline, String includeHeaders,
            boolean binaryContent) {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        mm.setMultipartSubType(multipartSubType);
        mm.setMultipartWithoutAttachment(Boolean.toString(multipartWithoutAttachment));
        mm.setHeadersInline(Boolean.toString(headersInline));
        mm.setIncludeHeaders(includeHeaders);
        mm.setBinaryContent(Boolean.toString(binaryContent));
        return dataFormat(mm);
    }

    /**
     * Uses the MIME Multipart data format
     *
     * @param multipartWithoutAttachment defines whether a message without attachment is also marshaled into a MIME
     *                                   Multipart (with only one body part).
     * @param headersInline              define the MIME Multipart headers as part of the message body or as Camel
     *                                   headers
     * @param binaryContent              have binary encoding for binary content (true) or use Base-64 encoding for
     *                                   binary content (false)
     */
    public T mimeMultipart(boolean multipartWithoutAttachment, boolean headersInline, boolean binaryContent) {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        mm.setMultipartWithoutAttachment(Boolean.toString(multipartWithoutAttachment));
        mm.setHeadersInline(Boolean.toString(headersInline));
        mm.setBinaryContent(Boolean.toString(binaryContent));
        return dataFormat(mm);
    }

    /**
     * Uses the PGP data format
     */
    public T pgp(String keyFileName, String keyUserid) {
        PGPDataFormat pgp = new PGPDataFormat();
        pgp.setKeyFileName(keyFileName);
        pgp.setKeyUserid(keyUserid);
        return dataFormat(pgp);
    }

    /**
     * Uses the PGP data format
     */
    public T pgp(String keyFileName, String keyUserid, String password) {
        PGPDataFormat pgp = new PGPDataFormat();
        pgp.setKeyFileName(keyFileName);
        pgp.setKeyUserid(keyUserid);
        pgp.setPassword(password);
        return dataFormat(pgp);
    }

    /**
     * Uses the PGP data format
     */
    public T pgp(String keyFileName, String keyUserid, String password, boolean armored, boolean integrity) {
        PGPDataFormat pgp = new PGPDataFormat();
        pgp.setKeyFileName(keyFileName);
        pgp.setKeyUserid(keyUserid);
        pgp.setPassword(password);
        pgp.setArmored(Boolean.toString(armored));
        pgp.setIntegrity(Boolean.toString(integrity));
        return dataFormat(pgp);
    }

    /**
     * Uses the Jackson XML data format
     */
    public T jacksonXml() {
        return dataFormat(new JacksonXMLDataFormat());
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType unmarshal type for xml jackson type
     */
    public T jacksonXml(Class<?> unmarshalType) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType unmarshal type for xml jackson type
     * @param jsonView      the view type for xml jackson type
     */
    public T jacksonXml(Class<?> unmarshalType, Class<?> jsonView) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format using the Jackson library turning pretty printing on or off
     *
     * @param prettyPrint turn pretty printing on or off
     */
    public T jacksonXml(boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType unmarshal type for xml jackson type
     * @param prettyPrint   turn pretty printing on or off
     */
    public T jacksonXml(Class<?> unmarshalType, boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType unmarshal type for xml jackson type
     * @param jsonView      the view type for xml jackson type
     * @param prettyPrint   turn pretty printing on or off
     */
    public T jacksonXml(Class<?> unmarshalType, Class<?> jsonView, boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        jacksonXMLDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType unmarshal type for xml jackson type
     * @param jsonView      the view type for xml jackson type
     * @param include       include such as <tt>ALWAYS</tt>, <tt>NON_NULL</tt>, etc.
     */
    public T jacksonXml(Class<?> unmarshalType, Class<?> jsonView, String include) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        jacksonXMLDataFormat.setInclude(include);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType unmarshal type for xml jackson type
     * @param jsonView      the view type for xml jackson type
     * @param include       include such as <tt>ALWAYS</tt>, <tt>NON_NULL</tt>, etc.
     * @param prettyPrint   turn pretty printing on or off
     */
    public T jacksonXml(Class<?> unmarshalType, Class<?> jsonView, String include, boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        jacksonXMLDataFormat.setInclude(include);
        jacksonXMLDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the JAXB data format
     */
    public T jaxb() {
        return dataFormat(new JaxbDataFormat());
    }

    /**
     * Uses the JAXB data format with context path
     */
    public T jaxb(String contextPath) {
        JaxbDataFormat dataFormat = new JaxbDataFormat();
        dataFormat.setContextPath(contextPath);
        return dataFormat(dataFormat);
    }

    /**
     * Uses the JAXB data format turning pretty printing on or off
     */
    public T jaxb(boolean prettyPrint) {
        return dataFormat(new JaxbDataFormat(prettyPrint));
    }

    /**
     * Uses the JSON data format using the Jackson library
     */
    public T json() {
        return dataFormat(new JsonDataFormat());
    }

    /**
     * Uses the JSON data format using the Jackson library turning pretty printing on or off
     *
     * @param prettyPrint turn pretty printing on or off
     */
    public T json(boolean prettyPrint) {
        JsonDataFormat json = new JsonDataFormat();
        json.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(json);
    }

    /**
     * Uses the JSON data format
     *
     * @param library the json library to use
     */
    public T json(JsonLibrary library) {
        return dataFormat(new JsonDataFormat(library));
    }

    /**
     * Uses the JSON data format
     *
     * @param library     the json library to use
     * @param prettyPrint turn pretty printing on or off
     */
    public T json(JsonLibrary library, boolean prettyPrint) {
        JsonDataFormat json = new JsonDataFormat(library);
        json.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(json);
    }

    /**
     * Uses the JSON data format
     *
     * @param type          the json type to use
     * @param unmarshalType unmarshal type for json jackson type
     */
    public T json(JsonLibrary type, Class<?> unmarshalType) {
        JsonDataFormat json = new JsonDataFormat(type);
        json.setUnmarshalType(unmarshalType);
        return dataFormat(json);
    }

    /**
     * Uses the JSON data format
     *
     * @param type          the json type to use
     * @param unmarshalType unmarshal type for json jackson type
     * @param prettyPrint   turn pretty printing on or off
     */
    public T json(JsonLibrary type, Class<?> unmarshalType, boolean prettyPrint) {
        JsonDataFormat json = new JsonDataFormat(type);
        json.setUnmarshalType(unmarshalType);
        json.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(json);
    }

    /**
     * Uses the Jackson JSON data format
     *
     * @param unmarshalType unmarshal type for json jackson type
     */
    public T json(Class<?> unmarshalType) {
        JsonDataFormat json = new JsonDataFormat(JsonLibrary.Jackson);
        json.setUnmarshalType(unmarshalType);
        return dataFormat(json);
    }

    /**
     * Uses the Jackson JSON data format
     *
     * @param unmarshalType unmarshal type for json jackson type
     * @param jsonView      the view type for json jackson type
     */
    public T json(Class<?> unmarshalType, Class<?> jsonView) {
        JsonDataFormat json = new JsonDataFormat(JsonLibrary.Jackson);
        json.setUnmarshalType(unmarshalType);
        json.setJsonView(jsonView);
        return dataFormat(json);
    }

    /**
     * Uses the Jackson JSON data format
     *
     * @param unmarshalType unmarshal type for json jackson type
     * @param jsonView      the view type for json jackson type
     * @param prettyPrint   turn pretty printing on or off
     */
    public T json(Class<?> unmarshalType, Class<?> jsonView, boolean prettyPrint) {
        JsonDataFormat json = new JsonDataFormat(JsonLibrary.Jackson);
        json.setUnmarshalType(unmarshalType);
        json.setJsonView(jsonView);
        json.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(json);
    }

    /**
     * Uses the Jackson JSON data format
     *
     * @param unmarshalType unmarshal type for json jackson type
     * @param jsonView      the view type for json jackson type
     * @param include       include such as <tt>ALWAYS</tt>, <tt>NON_NULL</tt>, etc.
     */
    public T json(Class<?> unmarshalType, Class<?> jsonView, String include) {
        JsonDataFormat json = new JsonDataFormat(JsonLibrary.Jackson);
        json.setUnmarshalType(unmarshalType);
        json.setJsonView(jsonView);
        json.setInclude(include);
        return dataFormat(json);
    }

    /**
     * Uses the Jackson JSON data format
     *
     * @param unmarshalType unmarshal type for json jackson type
     * @param jsonView      the view type for json jackson type
     * @param include       include such as <tt>ALWAYS</tt>, <tt>NON_NULL</tt>, etc.
     * @param prettyPrint   turn pretty printing on or off
     */
    public T json(Class<?> unmarshalType, Class<?> jsonView, String include, boolean prettyPrint) {
        JsonDataFormat json = new JsonDataFormat(JsonLibrary.Jackson);
        json.setUnmarshalType(unmarshalType);
        json.setJsonView(jsonView);
        json.setInclude(include);
        json.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(json);
    }

    /**
     * Uses the JSON API data format
     */
    public T jsonApi() {
        return dataFormat(new JsonApiDataFormat());
    }

    /**
     * Uses the protobuf data format
     */
    public T protobuf() {
        return dataFormat(new ProtobufDataFormat());
    }

    public T protobuf(Object defaultInstance) {
        ProtobufDataFormat dataFormat = new ProtobufDataFormat();
        dataFormat.setDefaultInstance(defaultInstance);
        return dataFormat(dataFormat);
    }

    public T protobuf(Object defaultInstance, String contentTypeFormat) {
        ProtobufDataFormat dataFormat = new ProtobufDataFormat();
        dataFormat.setDefaultInstance(defaultInstance);
        dataFormat.setContentTypeFormat(contentTypeFormat);
        return dataFormat(dataFormat);
    }

    public T protobuf(String instanceClassName) {
        return dataFormat(new ProtobufDataFormat(instanceClassName));
    }

    public T protobuf(String instanceClassName, String contentTypeFormat) {
        return dataFormat(new ProtobufDataFormat(instanceClassName, contentTypeFormat));
    }

    /**
     * Uses the Protobuf data format with given library
     */
    public T protobuf(ProtobufLibrary library) {
        return dataFormat(new ProtobufDataFormat(library));
    }

    /**
     * Uses the Protobuf data format with given library and unmarshalType
     */
    public T protobuf(ProtobufLibrary library, Class<?> unmarshalType) {
        ProtobufDataFormat protobufDataFormat = new ProtobufDataFormat();
        protobufDataFormat.setLibrary(library);
        protobufDataFormat.setUnmarshalType(unmarshalType);
        return dataFormat(protobufDataFormat);
    }

    /**
     * Uses the Protobuf data format with given library, unmarshalType and schemaResolver
     */
    public T protobuf(ProtobufLibrary library, Class<?> unmarshalType, String schemaResolver) {
        ProtobufDataFormat protobufDataFormat = new ProtobufDataFormat();
        protobufDataFormat.setLibrary(library);
        protobufDataFormat.setUnmarshalType(unmarshalType);
        protobufDataFormat.setSchemaResolver(schemaResolver);
        return dataFormat(protobufDataFormat);
    }

    /**
     * Uses the RSS data format
     */
    public T rss() {
        return dataFormat(new RssDataFormat());
    }

    /**
     * Uses the Soap v1.1 data format
     */
    public T soap() {
        return dataFormat(new SoapDataFormat());
    }

    /**
     * Uses the Soap v1.1 data format
     */
    public T soap(String contextPath) {
        return dataFormat(new SoapDataFormat(contextPath));
    }

    /**
     * Uses the Soap v1.1 data format
     */
    public T soap(String contextPath, String elementNameStrategyRef) {
        return dataFormat(new SoapDataFormat(contextPath, elementNameStrategyRef));
    }

    /**
     * Uses the Soap v1.1 data format
     */
    public T soap(String contextPath, Object elementNameStrategy) {
        return dataFormat(new SoapDataFormat(contextPath, elementNameStrategy));
    }

    /**
     * Uses the Soap v1.2 data format
     */
    public T soap12() {
        SoapDataFormat soap = new SoapDataFormat();
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the Soap v1.2 data format
     */
    public T soap12(String contextPath) {
        SoapDataFormat soap = new SoapDataFormat(contextPath);
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the Soap v1.2 data format
     */
    public T soap12(String contextPath, String elementNameStrategyRef) {
        SoapDataFormat soap = new SoapDataFormat(contextPath, elementNameStrategyRef);
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the Soap v1.2 data format
     */
    public T soap12(String contextPath, Object elementNameStrategy) {
        SoapDataFormat soap = new SoapDataFormat(contextPath, elementNameStrategy);
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the SWIFT MX data format
     */
    public T swiftMx() {
        return dataFormat(new SwiftMxDataFormat());
    }

    /**
     * Uses the SWIFT MX data format.
     */
    public T swiftMx(boolean writeInJson) {
        return dataFormat(new SwiftMxDataFormat(writeInJson));
    }

    /**
     * Uses the SWIFT MX data format.
     */
    public T swiftMx(boolean writeInJson, String readMessageId, Object readConfig) {
        return dataFormat(new SwiftMxDataFormat(writeInJson, readMessageId, readConfig));
    }

    /**
     * Uses the SWIFT MX data format.
     */
    public T swiftMx(boolean writeInJson, String readMessageId, String readConfigRef) {
        return dataFormat(new SwiftMxDataFormat(writeInJson, readMessageId, readConfigRef));
    }

    /**
     * Uses the SWIFT MX data format.
     */
    public T swiftMx(Object writeConfig, String readMessageId, Object readConfig) {
        return dataFormat(new SwiftMxDataFormat(writeConfig, readMessageId, readConfig));
    }

    /**
     * Uses the SWIFT MX data format.
     */
    public T swiftMx(String writeConfigRef, String readMessageId, String readConfigRef) {
        return dataFormat(new SwiftMxDataFormat(writeConfigRef, readMessageId, readConfigRef));
    }

    /**
     * Uses the SWIFT MT data format
     */
    public T swiftMt() {
        return dataFormat(new SwiftMtDataFormat());
    }

    /**
     * Uses the SWIFT MT data format.
     */
    public T swiftMt(boolean writeInJson) {
        return dataFormat(new SwiftMtDataFormat(Boolean.toString(writeInJson)));
    }

    /**
     * Uses the Syslog data format
     */
    public T syslog() {
        return dataFormat(new SyslogDataFormat());
    }

    /**
     * Uses the Thrift data format
     */
    public T thrift() {
        return dataFormat(new ThriftDataFormat());
    }

    public T thrift(Object defaultInstance) {
        ThriftDataFormat dataFormat = new ThriftDataFormat();
        dataFormat.setDefaultInstance(defaultInstance);
        return dataFormat(dataFormat);
    }

    public T thrift(Object defaultInstance, String contentTypeFormat) {
        ThriftDataFormat dataFormat = new ThriftDataFormat();
        dataFormat.setDefaultInstance(defaultInstance);
        dataFormat.setContentTypeFormat(contentTypeFormat);
        return dataFormat(dataFormat);
    }

    public T thrift(String instanceClassName) {
        return dataFormat(new ThriftDataFormat(instanceClassName));
    }

    public T thrift(String instanceClassName, String contentTypeFormat) {
        return dataFormat(new ThriftDataFormat(instanceClassName, contentTypeFormat));
    }

    /**
     * Return WellFormed HTML (an XML Document) either {@link java.lang.String} or {@link org.w3c.dom.Node}
     */
    public T tidyMarkup(Class<?> dataObjectType) {
        return dataFormat(new TidyMarkupDataFormat(dataObjectType));
    }

    /**
     * Return TidyMarkup in the default format as {@link org.w3c.dom.Node}
     */
    public T tidyMarkup() {
        return dataFormat(new TidyMarkupDataFormat(Node.class));
    }

    /**
     * Uses the YAML data format
     *
     * @param library the yaml library to use
     */
    public T yaml(YAMLLibrary library) {
        return dataFormat(new YAMLDataFormat(library));
    }

    /**
     * Uses the YAML data format
     *
     * @param library the yaml type to use
     * @param type    the type for json snakeyaml type
     */
    public T yaml(YAMLLibrary library, Class<?> type) {
        return dataFormat(new YAMLDataFormat(library, type));
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(byte[] passPhraseByte) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setPassPhraseByte(passPhraseByte);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(String secureTag, boolean secureTagContents, String passPhrase) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhrase(passPhrase);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String passPhrase) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhrase(passPhrase);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(String secureTag, boolean secureTagContents, String passPhrase, String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhrase(passPhrase);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, Map<String, String> namespaces, boolean secureTagContents, String passPhrase,
            String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhrase(passPhrase);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(String secureTag, boolean secureTagContents, byte[] passPhraseByte) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhraseByte(passPhraseByte);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(String secureTag, Map<String, String> namespaces, boolean secureTagContents, byte[] passPhraseByte) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhraseByte(passPhraseByte);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(String secureTag, boolean secureTagContents, byte[] passPhraseByte, String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhraseByte(passPhraseByte);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, Map<String, String> namespaces, boolean secureTagContents, byte[] passPhraseByte,
            String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setPassPhraseByte(passPhraseByte);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm,
            String keyCipherAlgorithm,
            String keyOrTrustStoreParametersId) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParametersRef(keyOrTrustStoreParametersId);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm,
            String keyCipherAlgorithm,
            String keyOrTrustStoreParametersId, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParametersRef(keyOrTrustStoreParametersId);
        xsdf.setKeyPassword(keyPassword);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm,
            String keyCipherAlgorithm,
            KeyStoreParameters keyOrTrustStoreParameters) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm,
            String keyCipherAlgorithm,
            KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        xsdf.setKeyPassword(keyPassword);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm,
            String keyOrTrustStoreParametersId) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParametersRef(keyOrTrustStoreParametersId);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm,
            String keyOrTrustStoreParametersId, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParametersRef(keyOrTrustStoreParametersId);
        xsdf.setKeyPassword(keyPassword);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm,
            KeyStoreParameters keyOrTrustStoreParameters) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm,
            KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        xsdf.setKeyPassword(keyPassword);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T xmlSecurity(
            String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm,
            KeyStoreParameters keyOrTrustStoreParameters, String keyPassword, String digestAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(Boolean.toString(secureTagContents));
        xsdf.setRecipientKeyAlias(recipientKeyAlias);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        xsdf.setKeyCipherAlgorithm(keyCipherAlgorithm);
        xsdf.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        xsdf.setDigestAlgorithm(digestAlgorithm);
        xsdf.setKeyPassword(keyPassword);
        return dataFormat(xsdf);
    }

    /**
     * Uses the Tar file data format
     */
    public T tarFile() {
        TarFileDataFormat tfdf = new TarFileDataFormat();
        return dataFormat(tfdf);
    }

    /**
     * Uses the ZIP deflater data format
     */
    public T zipDeflater() {
        ZipDeflaterDataFormat zdf = new ZipDeflaterDataFormat();
        return dataFormat(zdf);
    }

    /**
     * Uses the ZIP deflater data format
     */
    public T zipDeflater(int compressionLevel) {
        ZipDeflaterDataFormat zdf = new ZipDeflaterDataFormat();
        zdf.setCompressionLevel(Integer.toString(compressionLevel));
        return dataFormat(zdf);
    }

    /**
     * Uses the ZIP file data format
     */
    public T zipFile() {
        ZipFileDataFormat zfdf = new ZipFileDataFormat();
        return dataFormat(zfdf);
    }

    /**
     * Uses the ASN.1 file data format
     */
    public T asn1() {
        ASN1DataFormat asn1Df = new ASN1DataFormat();
        return dataFormat(asn1Df);
    }

    /**
     * Uses the ASN.1 file data format
     */
    public T asn1(String unmarshalType) {
        return dataFormat(new ASN1DataFormat(unmarshalType));
    }

    /**
     * Uses the ASN.1 file data format
     */
    public T asn1(Class<?> unmarshalType) {
        return dataFormat(new ASN1DataFormat(unmarshalType));
    }

    /**
     * Uses the ASN.1 file data format
     */
    public T asn1(Boolean usingIterator) {
        return dataFormat(new ASN1DataFormat(usingIterator));
    }

    /**
     * Uses the parquet-avro file data format
     */
    public T parquetAvro() {
        ParquetAvroDataFormat parquetAvroDataFormat = new ParquetAvroDataFormat();
        return dataFormat(parquetAvroDataFormat);
    }

    /**
     * Uses the parquet-avro file data format
     */
    public T parquetAvro(String unmarshalType) {
        return dataFormat(new ParquetAvroDataFormat(unmarshalType));
    }

    /**
     * Uses the parquet-avro file data format
     */
    public T parquetAvro(Class<?> unmarshalType) {
        return dataFormat(new ParquetAvroDataFormat(unmarshalType));
    }

    /**
     * Uses the FHIR JSON data format
     */
    public T fhirJson() {
        FhirJsonDataFormat jsonDataFormat = new FhirJsonDataFormat();
        return dataFormat(jsonDataFormat);
    }

    public T fhirJson(String version) {
        FhirJsonDataFormat jsonDataFormat = new FhirJsonDataFormat();
        jsonDataFormat.setFhirVersion(version);
        return dataFormat(jsonDataFormat);
    }

    public T fhirJson(boolean prettyPrint) {
        FhirJsonDataFormat jsonDataFormat = new FhirJsonDataFormat();
        jsonDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(jsonDataFormat);
    }

    public T fhirJson(String version, boolean prettyPrint) {
        FhirJsonDataFormat jsonDataFormat = new FhirJsonDataFormat();
        jsonDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        jsonDataFormat.setFhirVersion(version);
        return dataFormat(jsonDataFormat);
    }

    /**
     * Uses the FHIR XML data format
     */
    public T fhirXml() {
        FhirXmlDataFormat fhirXmlDataFormat = new FhirXmlDataFormat();
        return dataFormat(fhirXmlDataFormat);
    }

    public T fhirXml(String version) {
        FhirXmlDataFormat fhirXmlDataFormat = new FhirXmlDataFormat();
        fhirXmlDataFormat.setFhirVersion(version);
        return dataFormat(fhirXmlDataFormat);
    }

    public T fhirXml(boolean prettyPrint) {
        FhirXmlDataFormat fhirXmlDataFormat = new FhirXmlDataFormat();
        fhirXmlDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(fhirXmlDataFormat);
    }

    public T fhirXml(String version, boolean prettyPrint) {
        FhirXmlDataFormat fhirXmlDataFormat = new FhirXmlDataFormat();
        fhirXmlDataFormat.setFhirVersion(version);
        fhirXmlDataFormat.setPrettyPrint(Boolean.toString(prettyPrint));
        return dataFormat(fhirXmlDataFormat);
    }

    /**
     * Allows {@code null} as value of a body to unmarshall.
     *
     * @return the builder
     */
    public DataFormatClause<T> allowNullBody() {
        return allowNullBody(true);
    }

    /**
     * Indicates whether {@code null} is allowed as value of a body to unmarshall.
     *
     * @param  allowNullBody {@code true} if {@code null} is allowed as value of a body to unmarshall, {@code false}
     *                       otherwise
     * @return               the builder
     */
    public DataFormatClause<T> allowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
        return this;
    }

    @SuppressWarnings("unchecked")
    private T dataFormat(DataFormatDefinition dataFormatType) {
        switch (operation) {
            case Unmarshal:
                return (T) processorType.unmarshal(dataFormatType, allowNullBody);
            case Marshal:
                return (T) processorType.marshal(dataFormatType);
            default:
                throw new IllegalArgumentException("Unknown DataFormat operation: " + operation);
        }
    }
}
