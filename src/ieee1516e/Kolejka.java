package ieee1516e;

import java.util.LinkedList;

public class Kolejka {

    private Integer idKolejki;
    private LinkedList<Samochod> kolejkaSamochodow;

    public Kolejka(Integer idKolejki){
        this.idKolejki = idKolejki;
    }

    public Integer getIdKolejki() {
        return idKolejki;
    }

    public void addSamochodow(Samochod auto) {
        this.kolejkaSamochodow.add(auto);
    }

    public Samochod getFirstSamochow(){
        return this.kolejkaSamochodow.get(0);
    }

    @Override
    public String toString() {
        return "Kolejka{" +
                "idKolejki=" + idKolejki +
                ", kolejkaSamochodow=" + kolejkaSamochodow +
                '}';
    }
}
