package ieee1516e;

public class Samochod {

    int idSamochod;
    double vMost;
    double sPozostala;

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

    public double getvMost() {
        return vMost;
    }

    public void setvMost(double vMost) {
        this.vMost = vMost;
    }

    public double getsPozostala() {
        return sPozostala;
    }

    public void setsPozostala(double sPozostala) {
        this.sPozostala = sPozostala;
    }
}
