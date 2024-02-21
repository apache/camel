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
package org.apache.camel.dataformat.bindy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.dataformat.bindy.annotation.BindyConverter;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.OneToMany;
import org.apache.camel.dataformat.bindy.annotation.Section;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.dataformat.bindy.util.ConverterUtils;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The BindyCsvFactory is the class who allows to : Generate a model associated to a CSV record, bind data from a record
 * to the POJOs, export data of POJOs to a CSV record and format data into String, Date, Double, ... according to the
 * format/pattern defined
 */
public class BindyCsvFactory extends BindyAbstractFactory implements BindyFactory {

    private static final Logger LOG = LoggerFactory.getLogger(BindyCsvFactory.class);
    private static final String DOUBLE_QUOTES_SYMBOL = "\"";

    boolean isOneToMany;

    private Map<Integer, DataField> dataFields = new LinkedHashMap<>();
    private Map<Integer, Field> annotatedFields = new LinkedHashMap<>();
    private Map<String, Integer> sections = new HashMap<>();

    private int numberOptionalFields;
    private int numberMandatoryFields;
    private int totalFields;
    private int maxpos;

    private String separator;
    private boolean skipFirstLine;
    private boolean skipField;
    private boolean generateHeaderColumnNames;
    private boolean messageOrdered;
    private String quote;
    private boolean quoting;
    private boolean autospanLine;
    private boolean allowEmptyStream;
    private boolean quotingEscaped;
    private boolean quotingOnlyWhenNeeded;
    private boolean endWithLineBreak;
    private boolean removeQuotes;
    private boolean trimLine;

    public BindyCsvFactory(Class<?> type) throws Exception {
        super(type);

        // initialize specific parameters of the csv model
        initCsvModel();
    }

    /**
     * method uses to initialize the model representing the classes who will bind the data. This process will scan for
     * classes according to the package name provided, check the annotated classes and fields and retrieve the separator
     * of the CSV record
     *
     * @throws Exception
     */
    public void initCsvModel() {

        // Find annotated Datafields declared in the Model classes
        initAnnotatedFields();

        // initialize CSV parameter(s)
        // separator and skip first line from @CSVrecord annotation
        initCsvRecordParameters();
    }

    @Override
    public void initAnnotatedFields() {

        maxpos = 0;
        for (Class<?> cl : models) {
            List<Field> linkFields = new ArrayList<>();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Class retrieved: {}", cl.getName());
            }

            for (Field field : cl.getDeclaredFields()) {
                DataField dataField = field.getAnnotation(DataField.class);
                if (dataField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Position defined in the class: {}, position: {}, Field: {}",
                                cl.getName(), dataField.pos(), dataField);
                    }

                    if (dataField.required()) {
                        ++numberMandatoryFields;
                    } else {
                        ++numberOptionalFields;
                    }

                    int pos = dataField.pos();
                    if (annotatedFields.containsKey(pos)) {
                        Field f = annotatedFields.get(pos);
                        LOG.warn("Potentially invalid model: existing @DataField '{}' replaced by '{}'", f.getName(),
                                field.getName());
                    }
                    dataFields.put(pos, dataField);
                    annotatedFields.put(pos, field);
                    maxpos = Math.max(maxpos, pos);
                }

                Link linkField = field.getAnnotation(Link.class);

                if (linkField != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Class linked: {}, Field: {}", cl.getName(), field);
                    }
                    linkFields.add(field);
                }
            }

            if (!linkFields.isEmpty()) {
                annotatedLinkFields.put(cl.getName(), linkFields);
            }

            totalFields = numberMandatoryFields + numberOptionalFields;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Number of optional fields: {}", numberOptionalFields);
                LOG.debug("Number of mandatory fields: {}", numberMandatoryFields);
                LOG.debug("Total: {}", totalFields);
            }
        }

        if (annotatedFields.size() < maxpos) {
            LOG.debug("Potentially incomplete model: some csv fields may not be mapped to @DataField members");
        }
    }

    @Override
    public void bind(CamelContext camelContext, List<String> tokens, Map<String, Object> model, int line) throws Exception {

        int pos = 1;
        int counterMandatoryFields = 0;

        for (String data : tokens) {

            // Get DataField from model
            DataField dataField = dataFields.get(pos);

            // If a DataField can be skipped, it needs to check whether it is in dataFields keyset
            if (isSkipField()) {
                if (dataFields.keySet().contains(pos)) {
                    counterMandatoryFields
                            = setDataFieldValue(camelContext, model, line, pos, counterMandatoryFields, data, dataField);
                }
            } else {
                counterMandatoryFields
                        = setDataFieldValue(camelContext, model, line, pos, counterMandatoryFields, data, dataField);
            }

            ++pos;

        }

        LOG.debug("Counter mandatory fields: {}", counterMandatoryFields);

        if (counterMandatoryFields < numberMandatoryFields) {
            throw new IllegalArgumentException("Some mandatory fields are missing, line: " + line);
        }

        if (pos < totalFields) {
            setDefaultValuesForFields(model);
        }

    }

    private int setDataFieldValue(
            CamelContext camelContext, Map<String, Object> model, int line, int pos, int counterMandatoryFields, String data,
            DataField dataField)
            throws Exception {
        org.apache.camel.util.ObjectHelper.notNull(dataField,
                "No position " + pos + " defined for the field: " + data + ", line: " + line);

        if (dataField.trim()) {
            data = data.trim();
        }

        if (dataField.required()) {
            // Increment counter of mandatory fields
            ++counterMandatoryFields;

            // Check if content of the field is empty
            // This is not possible for mandatory fields
            if (data.isEmpty()) {
                throw new IllegalArgumentException(
                        "The mandatory field defined at the position " + pos + " is empty for the line: " + line);
            }
        }

        // Get Field to be setted
        Field field = annotatedFields.get(pos);
        field.setAccessible(true);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Pos: {}, Data: {}, Field type: {}", pos, data, field.getType());
        }

        // Create format object to format the field
        FormattingOptions formattingOptions = ConverterUtils.convert(dataField,
                field.getType(),
                field.getAnnotation(BindyConverter.class),
                getLocale());
        Format<?> format = formatFactory.getFormat(formattingOptions);

        // field object to be set
        Object modelField = model.get(field.getDeclaringClass().getName());

        // format the data received
        Object value;

        if (!data.isEmpty()) {
            try {
                if (quoting && quote != null && (data.contains("\\" + quote) || data.contains(quote)) && quotingEscaped) {
                    value = format.parse(data.replaceAll("\\\\" + quote, "\\" + quote));
                } else if (quote != null && quote.equals(DOUBLE_QUOTES_SYMBOL)
                        && data.contains(DOUBLE_QUOTES_SYMBOL + DOUBLE_QUOTES_SYMBOL) && !quotingEscaped) {
                    // If double-quotes are used to enclose fields, the two double
                    // quotes character must be replaced with one according to RFC 4180 section 2.7
                    value = format.parse(data.replace(DOUBLE_QUOTES_SYMBOL + DOUBLE_QUOTES_SYMBOL, DOUBLE_QUOTES_SYMBOL));
                } else {
                    value = format.parse(data);
                }
            } catch (FormatException ie) {
                throw new IllegalArgumentException(ie.getMessage() + ", position: " + pos + ", line: " + line, ie);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Parsing error detected for field defined at the position: " + pos + ", line: " + line, e);
            }
        } else {
            if (!dataField.defaultValue().isEmpty()) {
                value = format.parse(dataField.defaultValue());
            } else {
                value = getDefaultValueForPrimitive(field.getType());
            }
        }

        if (value != null && !dataField.method().isEmpty()) {
            Class<?> clazz;
            if (dataField.method().contains(".")) {
                clazz = camelContext.getClassResolver()
                        .resolveMandatoryClass(dataField.method().substring(0, dataField.method().lastIndexOf('.')));
            } else {
                clazz = field.getType();
            }

            String methodName = dataField.method().substring(dataField.method().lastIndexOf('.') + 1,
                    dataField.method().length());

            Method m = ReflectionHelper.findMethod(clazz, methodName, field.getType());
            if (m != null) {
                // this method must be static and return type
                // must be the same as the datafield and
                // must receive only the datafield value
                // as the method argument
                value = ObjectHelper.invokeMethod(m, null, value);
            } else {
                // fallback to method without parameter, that is on the value itself
                m = ReflectionHelper.findMethod(clazz, methodName);
                value = ObjectHelper.invokeMethod(m, value);
            }
        }

        field.set(modelField, value);
        return counterMandatoryFields;
    }

    @Override
    public String unbind(CamelContext camelContext, Map<String, Object> model) throws Exception {

        StringBuilder buffer = new StringBuilder();
        Map<Integer, List<String>> results = new HashMap<>();

        // Check if separator exists
        org.apache.camel.util.ObjectHelper.notNull(this.separator,
                "The separator has not been instantiated or property not defined in the @CsvRecord annotation");

        String carriageReturn = ConverterUtils.getStringCarriageReturn(getCarriageReturn());
        char separator = ConverterUtils.getCharDelimiter(this.getSeparator());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Separator converted: '0x{}', from: {}", Integer.toHexString(separator), this.getSeparator());
        }

        for (Class<?> clazz : models) {
            if (model.containsKey(clazz.getName())) {

                Object obj = model.get(clazz.getName());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Model object: {}, class: {}", obj, obj.getClass().getName());
                }
                if (obj != null) {

                    // Generate Csv table
                    generateCsvPositionMap(clazz, obj, results);

                }
            }
        }

        // Transpose result
        List<List<String>> l = new ArrayList<>();
        if (isOneToMany) {
            l = product(results);
        } else {
            // Convert Map<Integer, List> into List<List>
            TreeMap<Integer, List<String>> sortValues = new TreeMap<>(results);
            List<String> temp = new ArrayList<>();

            for (Entry<Integer, List<String>> entry : sortValues.entrySet()) {
                // Get list of values
                List<String> val = entry.getValue();

                // For one to one relation
                // There is only one item in the list
                String value = val.get(0);

                // Add the value to the temp array
                if (value != null) {
                    temp.add(value);
                } else {
                    temp.add("");
                }
            }

            l.add(temp);
        }

        Iterator<List<String>> it = l.iterator();
        while (it.hasNext()) {
            List<String> tokens = it.next();
            Iterator<String> itx = tokens.iterator();

            while (itx.hasNext()) {
                String res = itx.next();
                if (res != null) {
                    // RFC 4180 section 2.6 - fields containing line breaks, double
                    // quotes, and commas should be enclosed in double-quotes
                    boolean needsQuotes = quoting && quote != null &&
                            (!quotingOnlyWhenNeeded || res.contains(carriageReturn) || res.indexOf(separator) != -1
                                    || res.contains(quote));

                    if (needsQuotes) {
                        buffer.append(quote);

                        // CAMEL-7519 - improvement escape the token itself by prepending escape char
                        if (quotingEscaped && (res.contains("\\" + quote) || res.contains(quote))) {
                            buffer.append(res.replaceAll("\\" + quote, "\\\\" + quote));
                        } else if (!quotingEscaped && quote.equals(DOUBLE_QUOTES_SYMBOL) && res.contains(quote)) {
                            // If double-quotes are used to enclose fields, then a double-quote
                            // appearing inside a field must be escaped by preceding it with another
                            // double quote according to RFC 4180 section 2.7
                            buffer.append(res.replace(DOUBLE_QUOTES_SYMBOL, DOUBLE_QUOTES_SYMBOL + DOUBLE_QUOTES_SYMBOL));
                        } else {
                            buffer.append(res);
                        }

                        buffer.append(quote);
                    } else {
                        buffer.append(res);
                    }
                }

                if (itx.hasNext()) {
                    buffer.append(separator);
                }
            }

            if (it.hasNext()) {
                buffer.append(ConverterUtils.getStringCarriageReturn(getCarriageReturn()));
            }
        }

        return buffer.toString();
    }

    private List<List<String>> product(Map<Integer, List<String>> values) {
        TreeMap<Integer, List<String>> sortValues = new TreeMap<>(values);

        List<List<String>> product = new ArrayList<>();

        int idx = 0;
        int idxSize;
        do {
            idxSize = 0;
            List<String> v = new ArrayList<>();

            for (int ii = 1; ii <= sortValues.lastKey(); ii++) {
                List<String> l = values.get(ii);
                if (l == null) {
                    v.add("");
                    ++idxSize;
                    continue;
                }

                if (l.size() >= idx + 1) {
                    v.add(l.get(idx));
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value: {}, pos: {}, at: {}", l.get(idx), ii, idx);
                    }
                } else {
                    v.add(l.get(0));
                    ++idxSize;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value: {}, pos: {}, at index: {}", l.get(0), ii, 0);
                    }
                }
            }

            if (idxSize != sortValues.lastKey()) {
                product.add(v);
            }
            ++idx;

        } while (idxSize != sortValues.lastKey());

        return product;
    }

    /**
     * Generate a table containing the data formatted and sorted with their position/offset If the model is Ordered than
     * a key is created combining the annotation @Section and Position of the field If a relation @OneToMany is defined,
     * than we iterate recursively through this function The result is placed in the Map<Integer, List> results
     */
    private void generateCsvPositionMap(Class<?> clazz, Object obj, Map<Integer, List<String>> results) throws Exception {

        String result = "";

        for (Field field : clazz.getDeclaredFields()) {

            field.setAccessible(true);

            DataField datafield = field.getAnnotation(DataField.class);

            if (datafield != null) {

                if (obj != null) {

                    // Create format
                    FormattingOptions formattingOptions = ConverterUtils.convert(datafield,
                            field.getType(),
                            field.getAnnotation(BindyConverter.class),
                            getLocale());
                    Format<?> format = formatFactory.getFormat(formattingOptions);

                    // Get field value
                    Object value = field.get(obj);

                    // If the field value is empty, populate it with the default value
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(datafield.defaultValue())
                            && org.apache.camel.util.ObjectHelper.isEmpty(value)) {
                        value = datafield.defaultValue();
                    }

                    result = formatString(format, value);

                    if (datafield.trim()) {
                        result = result.trim();
                    }

                    if (datafield.clip() && result.length() > datafield.length()) {
                        result = result.substring(0, datafield.length());
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value to be formatted: {}, position: {}, and its formatted value: {}", value,
                                datafield.pos(), result);
                    }

                } else {
                    result = "";
                }

                Integer key;

                if (isMessageOrdered() && obj != null) {

                    // Generate a key using the number of the section
                    // and the position of the field
                    Integer key1 = sections.get(obj.getClass().getName());
                    Integer key2 = datafield.position();
                    Integer keyGenerated = generateKey(key1, key2);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Key generated: {}, for section: {}", String.valueOf(keyGenerated), key1);
                    }

                    key = keyGenerated;

                } else {
                    key = datafield.pos();
                }

                if (!results.containsKey(key)) {
                    List<String> list = new LinkedList<>();
                    list.add(result);
                    results.put(key, list);
                } else {
                    List<String> list = results.get(key);
                    list.add(result);
                }

            }

            OneToMany oneToMany = field.getAnnotation(OneToMany.class);
            if (oneToMany != null) {

                // Set global variable
                // Will be used during generation of CSV
                isOneToMany = true;

                List<?> list = (List<?>) field.get(obj);
                if (list != null) {

                    Iterator<?> it = list.iterator();
                    while (it.hasNext()) {
                        Object target = it.next();
                        generateCsvPositionMap(target.getClass(), target, results);
                    }

                } else {

                    // Call this function to add empty value
                    // in the table
                    generateCsvPositionMap(field.getClass(), null, results);
                }

            }
        }

    }

    /**
     * Generate for the first line the headers of the columns
     *
     * @return the headers columns
     */
    public String generateHeader() {

        Map<Integer, DataField> dataFieldsSorted = new TreeMap<>(dataFields);
        Iterator<Integer> it = dataFieldsSorted.keySet().iterator();

        StringBuilder builderHeader = new StringBuilder();

        while (it.hasNext()) {

            DataField dataField = dataFieldsSorted.get(it.next());

            // Retrieve the field
            Field field = annotatedFields.get(dataField.pos());
            // Change accessibility to allow to read protected/private fields
            field.setAccessible(true);

            // Get dataField
            final String res;
            if (!dataField.columnName().isEmpty()) {
                res = dataField.columnName();
            } else {
                res = field.getName();
            }

            if (quoting && quote != null) {
                builderHeader.append(quote);
            }
            if (quoting && quote != null && (res.contains("\\" + quote) || res.contains(quote)) && quotingEscaped) {
                builderHeader.append(res.replaceAll("\\" + quote, "\\\\" + quote));
            } else {
                builderHeader.append(res);
            }
            if (quoting && quote != null) {
                builderHeader.append(quote);
            }

            if (it.hasNext()) {
                builderHeader.append(ConverterUtils.getCharDelimiter(separator));
            }

        }

        return builderHeader.toString();
    }

    /**
     * Get parameters defined in @CsvRecord annotation
     */
    private void initCsvRecordParameters() {
        if (separator == null) {
            for (Class<?> cl : models) {

                // Get annotation @CsvRecord from the class
                CsvRecord csvRecord = cl.getAnnotation(CsvRecord.class);

                // Get annotation @Section from the class
                Section section = cl.getAnnotation(Section.class);

                if (csvRecord != null) {
                    LOG.debug("Csv record: {}", csvRecord);

                    // Get skipFirstLine parameter
                    skipFirstLine = csvRecord.skipFirstLine();
                    LOG.debug("Skip First Line parameter of the CSV: {}", skipFirstLine);

                    // Get skipFirstLine parameter
                    skipField = csvRecord.skipField();
                    LOG.debug("Skip Field parameter of the CSV: {}", skipField);

                    // Get generateHeaderColumnNames parameter
                    generateHeaderColumnNames = csvRecord.generateHeaderColumns();
                    LOG.debug("Generate header column names parameter of the CSV: {}", generateHeaderColumnNames);

                    // Get Separator parameter
                    org.apache.camel.util.ObjectHelper.notNull(csvRecord.separator(),
                            "No separator has been defined in the @Record annotation");
                    separator = csvRecord.separator();
                    LOG.debug("Separator defined for the CSV: {}", separator);

                    // Get carriage return parameter
                    crlf = csvRecord.crlf();
                    LOG.debug("Carriage return defined for the CSV: {}", crlf);

                    // Get isOrdered parameter
                    messageOrdered = csvRecord.isOrdered();
                    LOG.debug("Must CSV record be ordered: {}", messageOrdered);

                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(csvRecord.quote())) {
                        quote = csvRecord.quote();
                        LOG.debug("Quoting columns with: {}", quote);
                    }

                    quoting = csvRecord.quoting();
                    LOG.debug("CSV will be quoted: {}", quoting);

                    autospanLine = csvRecord.autospanLine();
                    LOG.debug("Autospan line in last record: {}", autospanLine);

                    // Get allowEmptyStream parameter
                    allowEmptyStream = csvRecord.allowEmptyStream();
                    LOG.debug("Allow empty stream parameter of the CSV: {}", allowEmptyStream);

                    // Get quotingEscaped parameter
                    quotingEscaped = csvRecord.quotingEscaped();
                    LOG.debug("Escape quote character flag of the CSV: {}", quotingEscaped);

                    // Get quotingOnlyWhenNeeded parameter
                    quotingOnlyWhenNeeded = csvRecord.quotingOnlyWhenNeeded();
                    LOG.debug("Quoting only when needed: {}", quotingOnlyWhenNeeded);

                    // Get endWithLineBreak parameter
                    endWithLineBreak = csvRecord.endWithLineBreak();
                    LOG.debug("End with line break: {}", endWithLineBreak);

                    removeQuotes = csvRecord.removeQuotes();
                    LOG.debug("Remove quotes: {}", removeQuotes);

                    trimLine = csvRecord.trimLine();
                    LOG.debug("Trim line: {}", trimLine);
                }

                if (section != null) {
                    // BigIntegerFormatFactory if section number is not null
                    org.apache.camel.util.ObjectHelper.notNull(section.number(), "No number has been defined for the section");

                    // Get section number and add it to the sections
                    sections.put(cl.getName(), section.number());
                }
            }
        }
    }

    /**
     * Set the default values for the non defined fields.
     *
     * @param  model                  the model which has its default fields set.
     * @throws IllegalAccessException if the underlying fields are inaccessible
     * @throws Exception              In case the field cannot be parsed
     */
    private void setDefaultValuesForFields(final Map<String, Object> model)
            throws Exception {
        // Set the default values, if defined
        for (int i = 1; i <= dataFields.size(); i++) {
            Field field = annotatedFields.get(i);
            field.setAccessible(true);
            DataField dataField = dataFields.get(i);
            Object modelField = model.get(field.getDeclaringClass().getName());
            if (field.get(modelField) == null && !dataField.defaultValue().isEmpty()) {
                FormattingOptions formattingOptions = ConverterUtils.convert(dataField,
                        field.getType(),
                        field.getAnnotation(BindyConverter.class),
                        getLocale());
                Format<?> format = formatFactory.getFormat(formattingOptions);
                Object value = format.parse(dataField.defaultValue());
                field.set(modelField, value);
            }
        }
    }

    /**
     * Find the separator used to delimit the CSV fields
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Flag indicating if the first line of the CSV must be skipped
     */
    public boolean getGenerateHeaderColumnNames() {
        return generateHeaderColumnNames;
    }

    /**
     * Find the separator used to delimit the CSV fields
     */
    public boolean getSkipFirstLine() {
        return skipFirstLine;
    }

    /**
     * Indicate if can skip fields
     *
     * @return boolean
     */
    public boolean isSkipField() {
        return this.skipField;
    }

    /**
     * If last record is to span the rest of the line
     */
    public boolean getAutospanLine() {
        return autospanLine;
    }

    /**
     * Flag indicating if the message must be ordered
     *
     * @return boolean
     */
    public boolean isMessageOrdered() {
        return messageOrdered;
    }

    public String getQuote() {
        return quote;
    }

    public Boolean getRemoveQuotes() {
        return removeQuotes;
    }

    public int getMaxpos() {
        return maxpos;
    }

    public boolean isAllowEmptyStream() {
        return allowEmptyStream;
    }

    public boolean isEndWithLineBreak() {
        return endWithLineBreak;
    }

    public boolean isTrimLine() {
        return trimLine;
    }
}
