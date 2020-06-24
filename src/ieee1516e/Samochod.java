package ieee1516e;

public class Samochod {

    int idSamochod;

    float vDroga;
    float vMost;
    int sPozostala;

    public Samochod(int idCar) {
        this.idSamochod = idCar;
    }

    @Override
    public String toString() {
        return "KolejkaSamochodow{" +
                "idSamochod =" + idSamochod +
                ", vBridge=" + vMost +
                ", distanceLeft=" + sPozostala +
                '}';
    }

    public int getIdSamochod() {
        return idSamochod;
    }

    public void setIdSamochod(int idSamochod) {
        this.idSamochod = idSamochod;
    }

    public float getvDroga() {
        return vDroga;
    }

    public void setvDroga(float vDroga) {
        this.vDroga = vDroga;
    }

    public float getvMost() {
        return vMost;
    }

    public void setvMost(float vMost) {
        this.vMost = vMost;
    }

    public int getsPozostala() {
        if(vMost > 0){
            sPozostala = 0;
        }else {
            sPozostala = Most.getDlugoscMostu();
        }
        return sPozostala;
    }

}
