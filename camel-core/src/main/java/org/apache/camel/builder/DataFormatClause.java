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
package org.apache.camel.builder;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.Deflater;

import org.w3c.dom.Node;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.dataformat.ASN1DataFormat;
import org.apache.camel.model.dataformat.AvroDataFormat;
import org.apache.camel.model.dataformat.Base64DataFormat;
import org.apache.camel.model.dataformat.BeanioDataFormat;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.model.dataformat.BoonDataFormat;
import org.apache.camel.model.dataformat.CastorDataFormat;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.model.dataformat.FhirJsonDataFormat;
import org.apache.camel.model.dataformat.FhirXmlDataFormat;
import org.apache.camel.model.dataformat.GzipDataFormat;
import org.apache.camel.model.dataformat.HL7DataFormat;
import org.apache.camel.model.dataformat.HessianDataFormat;
import org.apache.camel.model.dataformat.IcalDataFormat;
import org.apache.camel.model.dataformat.JacksonXMLDataFormat;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.JibxDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.LZFDataFormat;
import org.apache.camel.model.dataformat.MimeMultipartDataFormat;
import org.apache.camel.model.dataformat.PGPDataFormat;
import org.apache.camel.model.dataformat.ProtobufDataFormat;
import org.apache.camel.model.dataformat.RssDataFormat;
import org.apache.camel.model.dataformat.SerializationDataFormat;
import org.apache.camel.model.dataformat.SoapJaxbDataFormat;
import org.apache.camel.model.dataformat.StringDataFormat;
import org.apache.camel.model.dataformat.SyslogDataFormat;
import org.apache.camel.model.dataformat.TarFileDataFormat;
import org.apache.camel.model.dataformat.ThriftDataFormat;
import org.apache.camel.model.dataformat.TidyMarkupDataFormat;
import org.apache.camel.model.dataformat.XMLBeansDataFormat;
import org.apache.camel.model.dataformat.XMLSecurityDataFormat;
import org.apache.camel.model.dataformat.XStreamDataFormat;
import org.apache.camel.model.dataformat.XmlJsonDataFormat;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.apache.camel.model.dataformat.ZipDataFormat;
import org.apache.camel.model.dataformat.ZipFileDataFormat;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.jsse.KeyStoreParameters;

/**
 * An expression for constructing the different possible {@link org.apache.camel.spi.DataFormat}
 * options.
 *
 * @version 
 */
public class DataFormatClause<T extends ProcessorDefinition<?>> {
    private final T processorType;
    private final Operation operation;

    /**
     * {@link org.apache.camel.spi.DataFormat} operations.
     */
    public enum Operation {
        Marshal, Unmarshal
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
        dataFormat.setLineLength(lineLength);
        dataFormat.setLineSeparator(lineSeparator);
        dataFormat.setUrlSafe(urlSafe);
        return dataFormat(dataFormat);
    }

    /**
     * Uses the beanio data format
     */
    public T beanio(String mapping, String streamName) {
        BeanioDataFormat dataFormat = new BeanioDataFormat();
        dataFormat.setMapping(mapping);
        dataFormat.setStreamName(streamName);
        return dataFormat(dataFormat);
    }

    /**
     * Uses the beanio data format
     */
    public T beanio(String mapping, String streamName, String encoding) {
        BeanioDataFormat dataFormat = new BeanioDataFormat();
        dataFormat.setMapping(mapping);
        dataFormat.setStreamName(streamName);
        dataFormat.setEncoding(encoding);
        return dataFormat(dataFormat);
    }

    /**
     * Uses the beanio data format
     */
    public T beanio(String mapping, String streamName, String encoding,
                    boolean ignoreUnidentifiedRecords, boolean ignoreUnexpectedRecords, boolean ignoreInvalidRecords) {
        BeanioDataFormat dataFormat = new BeanioDataFormat();
        dataFormat.setMapping(mapping);
        dataFormat.setStreamName(streamName);
        dataFormat.setEncoding(encoding);
        dataFormat.setIgnoreUnidentifiedRecords(ignoreUnidentifiedRecords);
        dataFormat.setIgnoreUnexpectedRecords(ignoreUnexpectedRecords);
        dataFormat.setIgnoreInvalidRecords(ignoreInvalidRecords);
        return dataFormat(dataFormat);
    }
    
    /**
     * Uses the beanio data format
     */
    public T beanio(String mapping, String streamName, String encoding, String beanReaderErrorHandlerType) {
        BeanioDataFormat dataFormat = new BeanioDataFormat();
        dataFormat.setMapping(mapping);
        dataFormat.setStreamName(streamName);
        dataFormat.setEncoding(encoding);
        dataFormat.setBeanReaderErrorHandlerType(beanReaderErrorHandlerType);
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
        bindy.setType(type);
        bindy.setClassType(classType);
        return dataFormat(bindy);
    }

    /**
     * Uses the Bindy data format
     *
     * @param type      the type of bindy data format to use
     * @param classType the POJO class type
     * @param unwrapSingleInstance whether unmarshal should unwrap if there is a single instance in the result
     */
    public T bindy(BindyType type, Class<?> classType, boolean unwrapSingleInstance) {
        BindyDataFormat bindy = new BindyDataFormat();
        bindy.setType(type);
        bindy.setClassType(classType);
        bindy.setUnwrapSingleInstance(unwrapSingleInstance);
        return dataFormat(bindy);
    }

    /**
     * Uses the Boon data format
     *
     * @param classType the POJO class type
     */
    public T boon(Class<?> classType) {
        BoonDataFormat boon = new BoonDataFormat();
        boon.setUnmarshalType(classType);
        return dataFormat(boon);
    }

    /**
     * Uses the CSV data format
     */
    public T csv() {
        return dataFormat(new CsvDataFormat());
    }

    /**
     * Uses the CSV data format for a huge file.
     * Sequential access through an iterator.
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
     * Uses the Castor data format
     */
    public T castor() {
        return dataFormat(new CastorDataFormat());
    }

    /**
     * Uses the Castor data format
     *
     * @param mappingFile name of mapping file to locate in classpath
     */
    public T castor(String mappingFile) {
        CastorDataFormat castor = new CastorDataFormat();
        castor.setMappingFile(mappingFile);
        return dataFormat(castor);
    }

    /**
     * Uses the Castor data format
     *
     * @param mappingFile name of mapping file to locate in classpath
     * @param validation  whether validation is enabled or not
     */
    public T castor(String mappingFile, boolean validation) {
        CastorDataFormat castor = new CastorDataFormat();
        castor.setMappingFile(mappingFile);
        castor.setValidation(validation);
        return dataFormat(castor);
    }

    /**
     * Uses the GZIP deflater data format
     */
    public T gzip() {
        GzipDataFormat gzdf = new GzipDataFormat();
        return dataFormat(gzdf);
    }

    /**
     * Uses the Hessian data format
     */
    public T hessian() {
        return dataFormat(new HessianDataFormat());
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
        hl7.setValidate(validate);
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
        ical.setValidating(validating);
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
     * @param multipartWithoutAttachment defines whether a message without attachment is also marshaled
     *                                   into a MIME Multipart (with only one body part).
     * @param headersInline              define the MIME Multipart headers as part of the message body
     *                                   or as Camel headers
     * @param binaryContent              have binary encoding for binary content (true) or use Base-64
     *                                   encoding for binary content (false)
     */
    public T mimeMultipart(String multipartSubType, boolean multipartWithoutAttachment, boolean headersInline,
                           boolean binaryContent) {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        mm.setMultipartSubType(multipartSubType);
        mm.setMultipartWithoutAttachment(multipartWithoutAttachment);
        mm.setHeadersInline(headersInline);
        mm.setBinaryContent(binaryContent);
        return dataFormat(mm);
    }

    /**
     * Uses the MIME Multipart data format
     *
     * @param multipartSubType           the subtype of the MIME Multipart
     * @param multipartWithoutAttachment defines whether a message without attachment is also marshaled
     *                                   into a MIME Multipart (with only one body part).
     * @param headersInline              define the MIME Multipart headers as part of the message body
     *                                   or as Camel headers
     * @param includeHeaders            if headersInline is set to true all camel headers matching this
     *                                   regex are also stored as MIME headers on the Multipart
     * @param binaryContent              have binary encoding for binary content (true) or use Base-64
     *                                   encoding for binary content (false)
     */
    public T mimeMultipart(String multipartSubType, boolean multipartWithoutAttachment, boolean headersInline,
                           String includeHeaders, boolean binaryContent) {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        mm.setMultipartSubType(multipartSubType);
        mm.setMultipartWithoutAttachment(multipartWithoutAttachment);
        mm.setHeadersInline(headersInline);
        mm.setIncludeHeaders(includeHeaders);
        mm.setBinaryContent(binaryContent);
        return dataFormat(mm);
    }

    /**
     * Uses the MIME Multipart data format
     *
     * @param multipartWithoutAttachment defines whether a message without attachment is also marshaled
     *                                   into a MIME Multipart (with only one body part).
     * @param headersInline              define the MIME Multipart headers as part of the message body
     *                                   or as Camel headers
     * @param binaryContent              have binary encoding for binary content (true) or use Base-64
     *                                   encoding for binary content (false)
     */
    public T mimeMultipart(boolean multipartWithoutAttachment, boolean headersInline,
                           boolean binaryContent) {
        MimeMultipartDataFormat mm = new MimeMultipartDataFormat();
        mm.setMultipartWithoutAttachment(multipartWithoutAttachment);
        mm.setHeadersInline(headersInline);
        mm.setBinaryContent(binaryContent);
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
        pgp.setArmored(armored);
        pgp.setIntegrity(integrity);
        return dataFormat(pgp);
    }
    
    /**
     * Uses the Jackson XML data format
     */
    public T jacksonxml() {
        return dataFormat(new JacksonXMLDataFormat());
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType
     *            unmarshal type for xml jackson type
     */
    public T jacksonxml(Class<?> unmarshalType) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType
     *            unmarshal type for xml jackson type
     * @param jsonView
     *            the view type for xml jackson type
     */
    public T jacksonxml(Class<?> unmarshalType, Class<?> jsonView) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format using the Jackson library turning pretty
     * printing on or off
     * 
     * @param prettyPrint
     *            turn pretty printing on or off
     */
    public T jacksonxml(boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setPrettyPrint(prettyPrint);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType
     *            unmarshal type for xml jackson type
     * @param prettyPrint
     *            turn pretty printing on or off
     */
    public T jacksonxml(Class<?> unmarshalType, boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setPrettyPrint(prettyPrint);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType
     *            unmarshal type for xml jackson type
     * @param jsonView
     *            the view type for xml jackson type
     * @param prettyPrint
     *            turn pretty printing on or off
     */
    public T jacksonxml(Class<?> unmarshalType, Class<?> jsonView, boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        jacksonXMLDataFormat.setPrettyPrint(prettyPrint);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType
     *            unmarshal type for xml jackson type
     * @param jsonView
     *            the view type for xml jackson type
     * @param include
     *            include such as <tt>ALWAYS</tt>, <tt>NON_NULL</tt>, etc.
     */
    public T jacksonxml(Class<?> unmarshalType, Class<?> jsonView, String include) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        jacksonXMLDataFormat.setInclude(include);
        return dataFormat(jacksonXMLDataFormat);
    }

    /**
     * Uses the Jackson XML data format
     *
     * @param unmarshalType
     *            unmarshal type for xml jackson type
     * @param jsonView
     *            the view type for xml jackson type
     * @param include
     *            include such as <tt>ALWAYS</tt>, <tt>NON_NULL</tt>, etc.
     * @param prettyPrint
     *            turn pretty printing on or off
     */
    public T jacksonxml(Class<?> unmarshalType, Class<?> jsonView, String include, boolean prettyPrint) {
        JacksonXMLDataFormat jacksonXMLDataFormat = new JacksonXMLDataFormat();
        jacksonXMLDataFormat.setUnmarshalType(unmarshalType);
        jacksonXMLDataFormat.setJsonView(jsonView);
        jacksonXMLDataFormat.setInclude(include);
        jacksonXMLDataFormat.setPrettyPrint(prettyPrint);
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
     * Uses the JiBX data format.
     */
    public T jibx() {
        return dataFormat(new JibxDataFormat());
    }

    /**
     * Uses the JiBX data format with unmarshall class.
     */
    public T jibx(Class<?> unmarshallClass) {
        return dataFormat(new JibxDataFormat(unmarshallClass));
    }

    /**
     * Uses the JSON data format using the XStream json library
     */
    public T json() {
        return dataFormat(new JsonDataFormat());
    }

    /**
     * Uses the JSON data format using the XStream json library turning pretty printing on or off
     * 
     * @param prettyPrint turn pretty printing on or off
     */
    public T json(boolean prettyPrint) {
        JsonDataFormat json = new JsonDataFormat();
        json.setPrettyPrint(prettyPrint);
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
        json.setPrettyPrint(prettyPrint);
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
        json.setPrettyPrint(prettyPrint);
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
        json.setPrettyPrint(prettyPrint);
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
      * @param prettyPrint  turn pretty printing on or off
     */
    public T json(Class<?> unmarshalType, Class<?> jsonView, String include, boolean prettyPrint) {
        JsonDataFormat json = new JsonDataFormat(JsonLibrary.Jackson);
        json.setUnmarshalType(unmarshalType);
        json.setJsonView(jsonView);
        json.setInclude(include);
        json.setPrettyPrint(prettyPrint);
        return dataFormat(json);
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
     * Uses the RSS data format
     */
    public T rss() {
        return dataFormat(new RssDataFormat());
    }

    /**
     * Uses the Java Serialization data format
     */
    public T serialization() {
        return dataFormat(new SerializationDataFormat());
    }

    /**
     * Uses the Soap 1.1 JAXB data format
     */
    public T soapjaxb() {
        return dataFormat(new SoapJaxbDataFormat());
    }

    /**
     * Uses the Soap 1.1 JAXB data format
     */
    public T soapjaxb(String contextPath) {
        return dataFormat(new SoapJaxbDataFormat(contextPath));
    }

    /**
     * Uses the Soap 1.1 JAXB data format
     */
    public T soapjaxb(String contextPath, String elementNameStrategyRef) {
        return dataFormat(new SoapJaxbDataFormat(contextPath, elementNameStrategyRef));
    }

    /**
     * Uses the Soap 1.1 JAXB data format
     */
    public T soapjaxb(String contextPath, Object elementNameStrategy) {
        return dataFormat(new SoapJaxbDataFormat(contextPath, elementNameStrategy));
    }

    /**
     * Uses the Soap 1.2 JAXB data format
     */
    public T soapjaxb12() {
        SoapJaxbDataFormat soap = new SoapJaxbDataFormat();
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the Soap 1.2 JAXB data format
     */
    public T soapjaxb12(String contextPath) {
        SoapJaxbDataFormat soap = new SoapJaxbDataFormat(contextPath);
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the Soap 1.2 JAXB data format
     */
    public T soapjaxb12(String contextPath, String elementNameStrategyRef) {
        SoapJaxbDataFormat soap = new SoapJaxbDataFormat(contextPath, elementNameStrategyRef);
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the Soap JAXB data format
     */
    public T soapjaxb12(String contextPath, Object elementNameStrategy) {
        SoapJaxbDataFormat soap = new SoapJaxbDataFormat(contextPath, elementNameStrategy);
        soap.setVersion("1.2");
        return dataFormat(soap);
    }

    /**
     * Uses the String data format
     */
    public T string() {
        return string(null);
    }

    /**
     * Uses the String data format supporting encoding using given charset
     */
    public T string(String charset) {
        StringDataFormat sdf = new StringDataFormat();
        sdf.setCharset(charset);
        return dataFormat(sdf);
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
     * Return WellFormed HTML (an XML Document) either
     * {@link java.lang.String} or {@link org.w3c.dom.Node}
     */
    public T tidyMarkup(Class<?> dataObjectType) {
        return dataFormat(new TidyMarkupDataFormat(dataObjectType));
    }

    /**
     * Return TidyMarkup in the default format
     * as {@link org.w3c.dom.Node}
     */
    public T tidyMarkup() {
        return dataFormat(new TidyMarkupDataFormat(Node.class));
    }

    /**
     * Uses the XStream data format.
     * <p/>
     * Favor using {@link #xstream(String)} to pass in a permission
     */
    public T xstream() {
        return dataFormat(new XStreamDataFormat());
    }

    /**
     * Uses the xstream by setting the encoding or permission
     *
     * @param encodingOrPermission is either an encoding or permission syntax
     */
    public T xstream(String encodingOrPermission) {
        // is it an encoding? if not we assume its a permission
        if (Charset.isSupported(encodingOrPermission)) {
            return xstream(encodingOrPermission, (String) null);
        } else {
            return xstream(null, encodingOrPermission);
        }
    }

    /**
     * Uses the xstream by setting the encoding
     */
    public T xstream(String encoding, String permission) {
        XStreamDataFormat xdf = new XStreamDataFormat();
        xdf.setPermissions(permission);
        xdf.setEncoding(encoding);
        return dataFormat(xdf);
    }

    /**
     * Uses the xstream by permitting the java type
     *
     * @param type the pojo xstream should use as allowed permission
     */
    public T xstream(Class<?> type) {
        return xstream(null, type);
    }

    /**
     * Uses the xstream by permitting the java type
     *
     * @param encoding encoding to use
     * @param type the pojo class(es) xstream should use as allowed permission
     */
    public T xstream(String encoding, Class<?>... type) {
        CollectionStringBuffer csb = new CollectionStringBuffer(",");
        for (Class<?> clazz : type) {
            csb.append("+");
            csb.append(clazz.getName());
        }
        return xstream(encoding, csb.toString());
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
     * @param type the type for json snakeyaml type
     */
    public T yaml(YAMLLibrary library, Class<?> type) {
        return dataFormat(new YAMLDataFormat(library, type));
    }

    /**
     * Uses the XML Security data format
     */
    public T secureXML() {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents);
        return dataFormat(xsdf);
    }

    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, String passPhrase) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents, passPhrase);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String passPhrase) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents, passPhrase);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, String passPhrase, String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents, passPhrase, xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }
    
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String passPhrase, String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents, passPhrase, xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }
    
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, byte[] passPhraseByte) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(secureTagContents);
        xsdf.setPassPhraseByte(passPhraseByte);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, byte[] passPhraseByte) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(secureTagContents);
        xsdf.setPassPhraseByte(passPhraseByte);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, byte[] passPhraseByte, String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setSecureTagContents(secureTagContents);
        xsdf.setPassPhraseByte(passPhraseByte);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }
    
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, byte[] passPhraseByte, String xmlCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat();
        xsdf.setSecureTag(secureTag);
        xsdf.setNamespaces(namespaces);
        xsdf.setSecureTagContents(secureTagContents);
        xsdf.setPassPhraseByte(passPhraseByte);
        xsdf.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        return dataFormat(xsdf);
    }
    
    /**
     * @deprecated Use {@link #secureXML(String, Map, boolean, String, String, String, String)} instead.
     * Uses the XML Security data format
     */
    @Deprecated
    public T secureXML(String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm, 
            String keyCipherAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, keyCipherAlgorithm);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm, 
            String keyCipherAlgorithm, String keyOrTrustStoreParametersId) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
            keyCipherAlgorithm, keyOrTrustStoreParametersId);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm, 
            String keyCipherAlgorithm, String keyOrTrustStoreParametersId, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
            keyCipherAlgorithm, keyOrTrustStoreParametersId, keyPassword);
        return dataFormat(xsdf);
    }    
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm, 
            String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
            keyCipherAlgorithm, keyOrTrustStoreParameters);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, boolean secureTagContents, String recipientKeyAlias, String xmlCipherAlgorithm, 
            String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
            keyCipherAlgorithm, keyOrTrustStoreParameters, keyPassword);
        return dataFormat(xsdf);
    }    
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
                keyCipherAlgorithm, keyOrTrustStoreParametersId);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
                keyCipherAlgorithm, keyOrTrustStoreParametersId, keyPassword);
        return dataFormat(xsdf);
    }    
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
                keyCipherAlgorithm, keyOrTrustStoreParameters);
        return dataFormat(xsdf);
    }
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
                keyCipherAlgorithm, keyOrTrustStoreParameters, keyPassword);
        return dataFormat(xsdf);
    }   
    
    /**
     * Uses the XML Security data format
     */
    public T secureXML(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword,
            String digestAlgorithm) {
        XMLSecurityDataFormat xsdf = new XMLSecurityDataFormat(secureTag, namespaces, secureTagContents, recipientKeyAlias, xmlCipherAlgorithm, 
                keyCipherAlgorithm, keyOrTrustStoreParameters, keyPassword, digestAlgorithm);
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
     * Uses the xmlBeans data format
     */
    public T xmlBeans() {
        return dataFormat(new XMLBeansDataFormat());
    }

    /**
     * Uses the xmljson dataformat, based on json-lib
     */
    @Deprecated
    public T xmljson() {
        return dataFormat(new XmlJsonDataFormat());
    }
    
    /**
     * Uses the xmljson dataformat, based on json-lib, initializing custom options with a Map
     */
    @Deprecated
    public T xmljson(Map<String, String> options) {
        return dataFormat(new XmlJsonDataFormat(options));
    }
    
    /**
     * Uses the ZIP deflater data format
     */
    public T zip() {
        ZipDataFormat zdf = new ZipDataFormat(Deflater.DEFAULT_COMPRESSION);
        return dataFormat(zdf);
    }

    /**
     * Uses the ZIP deflater data format
     */
    public T zip(int compressionLevel) {
        ZipDataFormat zdf = new ZipDataFormat(compressionLevel);
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
    
    public T asn1(String clazzName) {
        return dataFormat(new ASN1DataFormat(clazzName));
    }
    
    public T asn1(Boolean usingIterator) {
        return dataFormat(new ASN1DataFormat(usingIterator));
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
        jsonDataFormat.setPrettyPrint(prettyPrint);
        return dataFormat(jsonDataFormat);
    }

    public T fhirJson(String version, boolean prettyPrint) {
        FhirJsonDataFormat jsonDataFormat = new FhirJsonDataFormat();
        jsonDataFormat.setPrettyPrint(prettyPrint);
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
        fhirXmlDataFormat.setPrettyPrint(prettyPrint);
        return dataFormat(fhirXmlDataFormat);
    }

    public T fhirXml(String version, boolean prettyPrint) {
        FhirXmlDataFormat fhirXmlDataFormat = new FhirXmlDataFormat();
        fhirXmlDataFormat.setFhirVersion(version);
        fhirXmlDataFormat.setPrettyPrint(prettyPrint);
        return dataFormat(fhirXmlDataFormat);
    }

    @SuppressWarnings("unchecked")
    private T dataFormat(DataFormatDefinition dataFormatType) {
        switch (operation) {
        case Unmarshal:
            return (T) processorType.unmarshal(dataFormatType);
        case Marshal:
            return (T) processorType.marshal(dataFormatType);
        default:
            throw new IllegalArgumentException("Unknown DataFormat operation: " + operation);
        }
    }
}
