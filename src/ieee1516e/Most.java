package ieee1516e;

import java.util.ArrayList;
import java.util.LinkedList;

public class Most {

    private static final Integer MAX_NUM_AUT = 10;

    private StanSwiatel stanSwiatel;
    private LinkedList<Samochod> przejazdzająceSamochod;

    public Most(){
        this.stanSwiatel = StanSwiatel.CZERWONY;
        this.przejazdzająceSamochod = new LinkedList<>();
    }

    public boolean czyJestPusty() {
        if(przejazdzająceSamochod.size() == 0){
            return true;
        }else {
            return false;
        }
    }

    public boolean czyJestPelny() {
        if(przejazdzająceSamochod.size() >= MAX_NUM_AUT){
            return true;
        }else {
            return false;
        }
    }

    public StanSwiatel getStanSwiatel() {
        return stanSwiatel;
    }

    public void setStanSwiatel(StanSwiatel stanSwiatel) {
        this.stanSwiatel = stanSwiatel;
    }

    public LinkedList<Samochod> getPrzejazdzająceSamochod() {
        return przejazdzająceSamochod;
    }

    public void setPrzejazdzająceSamochod(LinkedList<Samochod> przejazdzająceSamochod) {
        this.przejazdzająceSamochod = przejazdzająceSamochod;
    }
}
