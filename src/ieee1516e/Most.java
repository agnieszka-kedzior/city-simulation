package ieee1516e;

import java.util.LinkedList;

public class Most {

    private static final Integer MAX_NUM_AUT = 10;

    private static final int DLUGOSC_MOSTU = 10;

    private StanSwiatel stanSwiatel;
    private LinkedList<Samochod> samochodyNaMoscie;

    public Most(){
        this.stanSwiatel = StanSwiatel.CZERWONY;
        this.samochodyNaMoscie = new LinkedList<>();
    }

    public boolean czyJestPusty() {
        if(samochodyNaMoscie.size() == 0){
            return true;
        }else {
            return false;
        }
    }

    public boolean czyJestPelny() {
        if(samochodyNaMoscie.size() >= MAX_NUM_AUT){
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

    public void dodajPrzejezdzajacySamochod(Samochod samochod){
        samochodyNaMoscie.add(samochod);
    }

    public void usunPrzejezdzajacySamochod(Samochod samochod){
        samochodyNaMoscie.remove(samochod);
    }

    public Samochod pierwszeAuto(){
        return samochodyNaMoscie.get(0);
    }

    public LinkedList<Samochod> getSamochodyNaMoscie() {
        return samochodyNaMoscie;
    }

    public static int getDlugoscMostu() {
        return DLUGOSC_MOSTU;
    }
}
