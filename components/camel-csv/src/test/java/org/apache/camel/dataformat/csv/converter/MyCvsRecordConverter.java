package org.apache.camel.dataformat.csv.converter;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.dataformat.csv.CsvRecordConverter;
import org.apache.commons.csv.CSVRecord;

/**
 * Test {@link CsvRecordConverter} implementation.
 * <p>
 * This implementation is explicitely created in a subpackage to check the
 * visibility of {@link CsvRecordConverter}.
 * </p>
 */
public class MyCvsRecordConverter implements CsvRecordConverter<List<String>> {

    private final String[] record;

    public MyCvsRecordConverter(String... record) {
        assert record != null : "Unspecified record";
        this.record = record;
    }

    @Override
    public List<String> convertRecord(CSVRecord record) {
        assert record != null : "Unspecified record";
        return Arrays.asList(this.record);
    }
}
