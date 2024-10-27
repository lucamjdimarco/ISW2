package model;

public class AcumeModel {

    private int id;
    private double size;
    private double probability;
    private String value;

    public AcumeModel(int id, double size, double probability, String value) {
        this.id = id;
        this.size = size;
        this.probability = probability;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public double getSize() {
        return size;
    }

    public double getProbability() {
        return probability;
    }

    public String getValue() {
        return value;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "AcumeModel{" +
                "id=" + id +
                ", size=" + size +
                ", probability=" + probability +
                ", value='" + value + '\'' +
                '}';
    }
}
