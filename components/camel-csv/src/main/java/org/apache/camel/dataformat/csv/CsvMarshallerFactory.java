package org.apache.camel.dataformat.csv;

import org.apache.commons.csv.CSVFormat;

/**
 * A {@link CsvMarshaller} factory.
 */
public interface CsvMarshallerFactory {

    final static CsvMarshallerFactory DEFAULT = new CsvMarshallerFactory() {
        @Override
        public CsvMarshaller create(CSVFormat format, CsvDataFormat dataFormat) {
            return CsvMarshaller.create(format, dataFormat);
        }
    };

    /**
     * Creates and returns a new {@link CsvMarshaller}.
     *
     * @param format     the <b>CSV</b> format. Can NOT be <code>null</code>.
     * @param dataFormat the <b>CSV</b> data format. Can NOT be <code>null</code>.
     * @return a new {@link CsvMarshaller}.
     */
    public CsvMarshaller create(CSVFormat format, CsvDataFormat dataFormat);
}
