package ieee1516e;

import java.util.ArrayList;
import java.util.LinkedList;

public class Most {

    private boolean jestPusty;
    private boolean jestPelny;
    private StanSwiatel stanSwiatel;
    private Integer maxLiczbaSamochodow = 10;
    private LinkedList<Samochod> przejazdzająceSamochod;

    public Most(){
        this.jestPusty = true;
        this.stanSwiatel = StanSwiatel.CZERWONY;
        this.przejazdzająceSamochod = new LinkedList<>();
    }

    public boolean isJestPusty() {
        return jestPusty;
    }

    public void setJestPusty(boolean jestPusty) {
        this.jestPusty = jestPusty;
    }

    public boolean isJestPelny() {
        return jestPelny;
    }

    public void setJestPelny(boolean jestPelny) {
        this.jestPelny = jestPelny;
    }

    public StanSwiatel getStanSwiatel() {
        return stanSwiatel;
    }

    public void setStanSwiatel(StanSwiatel stanSwiatel) {
        this.stanSwiatel = stanSwiatel;
    }

    public Integer getMaxLiczbaSamochodow() {
        return maxLiczbaSamochodow;
    }

    public void setMaxLiczbaSamochodow(Integer maxLiczbaSamochodow) {
        this.maxLiczbaSamochodow = maxLiczbaSamochodow;
    }

    public LinkedList<Samochod> getPrzejazdzająceSamochod() {
        return przejazdzająceSamochod;
    }

    public void setPrzejazdzająceSamochod(LinkedList<Samochod> przejazdzająceSamochod) {
        this.przejazdzająceSamochod = przejazdzająceSamochod;
    }
}
