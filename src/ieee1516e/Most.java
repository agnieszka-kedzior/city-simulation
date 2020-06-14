package ieee1516e;

public class Most {

    private boolean jestPusty;
    private boolean jestPelny;
    private StanSwiatel stanSwiatel;
    private Integer maxLiczbaSamochodow = 10;

    public Most(){
        jestPusty = true;
        stanSwiatel = StanSwiatel.CZERWONY;
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
}
