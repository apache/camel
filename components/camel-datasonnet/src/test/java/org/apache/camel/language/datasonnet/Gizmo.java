package org.apache.camel.language.datasonnet;

import java.util.Date;
import java.util.List;
import java.util.Objects;

public class Gizmo {
    private String name;
    private int quantity;
    private List<String> colors;
    private boolean inStock;
    private Manufacturer manufacturer;
    private Date date;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public List<String> getColors() {
        return colors;
    }

    public void setColors(List<String> colors) {
        this.colors = colors;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    public Manufacturer getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(Manufacturer manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "Gizmo{" +
                "name='" + name + '\'' +
                ", quantity=" + quantity +
                ", colors=" + colors +
                ", inStock=" + inStock +
                ", manufacturer=" + manufacturer +
                ", date=" + date +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Gizmo gizmo = (Gizmo) o;
        return getQuantity() == gizmo.getQuantity() &&
                isInStock() == gizmo.isInStock() &&
                Objects.equals(getName(), gizmo.getName()) &&
                Objects.equals(getColors(), gizmo.getColors()) &&
                Objects.equals(date, gizmo.getDate()) &&
                Objects.equals(getManufacturer(), gizmo.getManufacturer());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getQuantity(), getColors(), isInStock(), getManufacturer(), getDate());
    }
}
