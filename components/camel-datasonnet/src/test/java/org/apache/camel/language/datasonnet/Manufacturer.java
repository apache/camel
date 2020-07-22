package org.apache.camel.language.datasonnet;

import java.util.Objects;

public class Manufacturer {
    private String manufacturerName;
    private String manufacturerCode;

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public void setManufacturerCode(String manufacturerCode) {
        this.manufacturerCode = manufacturerCode;
    }

    @Override
    public String toString() {
        return "Manufacturer{" +
                "manufacturerName='" + manufacturerName + '\'' +
                ", manufacturerCode='" + manufacturerCode + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Manufacturer that = (Manufacturer) o;
        return Objects.equals(getManufacturerName(), that.getManufacturerName()) &&
                Objects.equals(getManufacturerCode(), that.getManufacturerCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getManufacturerName(), getManufacturerCode());
    }
}