package org.apache.camel.model.dataformat;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

@Metadata(firstVersion = "4.0.0", label = "dataformat,transformation,file", title = "Parquet File")
@XmlRootElement(name = "parquetAvro")
@XmlAccessorType(XmlAccessType.FIELD)
public class ParquetAvroDataFormat extends DataFormatDefinition {

    @XmlTransient
    private Class<?> unmarshalType;

    @XmlAttribute(name = "unmarshalType")
    private String unmarshalTypeName;

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class to use when unmarshalling.
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    /**
     * Class to use when unmarshalling.
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public ParquetAvroDataFormat() {
        super("parquetAvro");
    }

    public ParquetAvroDataFormat(String unmarshalTypeName) {
        this();
        setUnmarshalTypeName(unmarshalTypeName);
    }

    public ParquetAvroDataFormat(Class<?> unmarshalType) {
        this();
        this.unmarshalType = unmarshalType;
    }

    private ParquetAvroDataFormat(Builder builder) {
        this();
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.unmarshalType = builder.unmarshalType;
    }

    /**
     * {@code Builder} is a specific builder for {@link ParquetAvroDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<ParquetAvroDataFormat> {

        private Class<?> unmarshalType;
        private String unmarshalTypeName;
        /**
         * Class to use when unmarshalling.
         */
        public Builder unmarshalTypeName(String unmarshalTypeName) {
            this.unmarshalTypeName = unmarshalTypeName;
            return this;
        }

        /**
         * Class to use when unmarshalling.
         */
        public Builder unmarshalType(Class<?> unmarshalType) {
            this.unmarshalType = unmarshalType;
            return this;
        }

        @Override
        public ParquetAvroDataFormat end() {
            return new ParquetAvroDataFormat(this);
        }
    }

}
