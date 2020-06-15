package ieee1516e;

import java.util.ArrayList;
import java.util.LinkedList;

public class Kolejka {

    private Integer idKolejki;
    private ArrayList<Samochod> kolejkaSamochod;

    public Kolejka(Integer idKolejki)
    {
        this.idKolejki = idKolejki;
        this.kolejkaSamochod = new ArrayList<>();
    }

    public void addAuto(Samochod samochod){
        kolejkaSamochod.add(samochod);
    }

    public Samochod pierwszeAuto(){
        return kolejkaSamochod.get(0);
    }

    @Override
    public String toString() {
        return "Kolejka{" +
                "idKolejki=" + idKolejki +
                ", kolejkaSamochodow=" + kolejkaSamochod +
                '}';
    }
}
