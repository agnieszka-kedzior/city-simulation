package ieee1516e.KolejkaFED;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAASCIIstring;
import hla.rti1516e.encoding.HLAboolean;
import hla.rti1516e.encoding.HLAinteger32LE;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import ieee1516e.Kolejka;
import ieee1516e.Samochod;
import ieee1516e.StanSwiatel;
import org.portico.impl.hla13.types.HLA13Set;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;


public class KolejkaFederat {
    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------
    public static final int ITERATIONS = 20;
    public static final String READY_TO_RUN = "ReadyToRun";
    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private RTIambassador rtiamb;
    private KolejkaFederatAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;

    protected ObjectClassHandle kolejkaHandle;
    protected AttributeHandle kolejkaNumerHandle;
    protected AttributeHandle kolejkaPierwszyHandle;

    protected ObjectClassHandle mostHandle;
    protected AttributeHandle mostCzyPustyHandle;
    protected AttributeHandle mostCzyPelnyHandle;
    protected AttributeHandle mostKierunekHandle;

    protected ObjectClassHandle autoHandle;
    protected AttributeHandle autoIdHandle;
    protected AttributeHandle autoPredkoscDrogaHandle;
    protected AttributeHandle autoPredkoscMostHandle;
    protected AttributeHandle autoDrogaHandle;

    protected InteractionClassHandle dolaczenieDoKolejkiHandle;
    protected ParameterHandle dolaczaAutoIdHandle;

    protected InteractionClassHandle opuszczenieKolejkiHandle;
    protected ParameterHandle opuszczenieAutoIdHandle;

    protected InteractionClassHandle wjazdNaMostHandle;
    protected ParameterHandle wjazdAutoIdHandle;
    protected ParameterHandle wjazdPredkoscAutaHandle;

    private Kolejka kolejkaMiastoA;
    private Kolejka kolejkaMiastoB;
    protected Boolean wjazdNaMostMiastoA;
    protected Boolean wjazdNaMostMiastoB;
    protected String przejazdDlaMiasta;

    //----------------------------------------------------------
    //                      CONSTRUCTORS
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                    INSTANCE METHODS
    //----------------------------------------------------------
    private void log(String message) {
        System.out.println("KolejkaFederate   : " + message);
    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runFederate(String federateName) throws Exception {
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new KolejkaFederatAmbassador(this);
        rtiamb.connect(fedamb, CallbackModel.HLA_EVOKED);

        log("Creating Federation...");
        try {
            URL[] modules = new URL[]{
                    (new File("foms/HLA.xml")).toURI().toURL(),
            };

            rtiamb.createFederationExecution("Federation", modules);
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception loading one of the FOM modules from disk: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        URL[] joinModules = new URL[]{
                (new File("foms/HLA.xml")).toURI().toURL()
        };

        rtiamb.joinFederationExecution( federateName,
                "FederateType",
                "Federation",
                joinModules );

        log("Joined Federation as " + federateName);

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();

        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);
        // wait until the point is announced
        while (fedamb.isAnnounced == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        enableTimePolicy();
        log("Time Policy Enabled");

        kolejkaMiastoA = new Kolejka(1);
        kolejkaMiastoB = new Kolejka(2);

        wjazdNaMostMiastoA = false;
        wjazdNaMostMiastoB = false;
        przejazdDlaMiasta = "N/A";

        subscribeMost();
        subscribeAuto();
        publishKolejke();
        publishWjazdNaMost();
        publishOpuszczenieKolejki();
        subscribeDolaczenieDoKolejki();
        log("Published and Subscribed");

        ObjectInstanceHandle objKolejkaHandle = rtiamb.registerObjectInstance(kolejkaHandle);

        while (fedamb.isRunning){
            updateStanKolejki(objKolejkaHandle, 1);
            updateStanKolejki(objKolejkaHandle, 2);

            if(przejazdDlaMiasta.equals("A") && wjazdNaMostMiastoA && !wjazdNaMostMiastoB){
                if(kolejkaMiastoA.getKolejkaSamochod().size() > 0){
                    sendInteractionWjazdNaMost(kolejkaMiastoA.pierwszeAuto().getIdSamochod());
                    sendInteractionOpuszczenieKolejki(kolejkaMiastoA.pierwszeAuto().getIdSamochod());

                    kolejkaMiastoA.removeAuto(kolejkaMiastoA.pierwszeAuto());
                }
            }else if(przejazdDlaMiasta.equals("A") && wjazdNaMostMiastoA && wjazdNaMostMiastoB){
                log("Z mostu zjezdzają samochody z miasta B");
            }else if(przejazdDlaMiasta.equals("A") && !wjazdNaMostMiastoA && !wjazdNaMostMiastoB){
                log("Most jest pełny, ruch z miasta A wstrzymany");
            }else if(przejazdDlaMiasta.equals("B") && wjazdNaMostMiastoB && !wjazdNaMostMiastoA){
                if(kolejkaMiastoB.getKolejkaSamochod().size() > 0){
                    sendInteractionWjazdNaMost(kolejkaMiastoB.pierwszeAuto().getIdSamochod());
                    sendInteractionOpuszczenieKolejki(kolejkaMiastoB.pierwszeAuto().getIdSamochod());

                    kolejkaMiastoB.removeAuto(kolejkaMiastoB.pierwszeAuto());
                }
            }else if(przejazdDlaMiasta.equals("B") && !wjazdNaMostMiastoB && wjazdNaMostMiastoA){
                log("Z mostu zjezdzają samochody z miasta A");
            }else if(przejazdDlaMiasta.equals("B") && !wjazdNaMostMiastoB && !wjazdNaMostMiastoA){
                log("Most jest pełny, ruch z miasta B wstrzymany");
            }

            advanceTime(1.0);
            log("Time Advanced to " + fedamb.federateTime);
        }

        deleteObject(objKolejkaHandle);

        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        log("Resigned from Federation");

        try {
            rtiamb.destroyFederationExecution("Federation");
            log("Destroyed Federation");
        } catch (FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (FederatesCurrentlyJoined fcj) {
            log("Didn't destroy federation, federates still joined");
        }
    }

    private void enableTimePolicy() throws Exception {
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation(lookahead);

        // tick until we get the callback
        while (fedamb.isRegulating == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
        this.rtiamb.enableTimeConstrained();

        // tick until we get the callback
        while (fedamb.isConstrained == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void publishKolejke() throws RTIexception {
        this.kolejkaHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Kolejka");
        this.kolejkaNumerHandle = rtiamb.getAttributeHandle(kolejkaHandle, "idKolejka");
        this.kolejkaPierwszyHandle = rtiamb.getAttributeHandle(kolejkaHandle,"idPierwszy");

        AttributeHandleSet kolejkaAttributes = rtiamb.getAttributeHandleSetFactory().create();
        kolejkaAttributes.add(kolejkaNumerHandle);
        kolejkaAttributes.add(kolejkaPierwszyHandle);

        rtiamb.publishObjectClassAttributes(kolejkaHandle, kolejkaAttributes);

        log("Kolejka Publishing Set");
    }

    private void subscribeMost() throws RTIexception {
        this.mostHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Most");
        this.mostCzyPelnyHandle = rtiamb.getAttributeHandle(mostHandle, "czyPelny");
        this.mostCzyPustyHandle = rtiamb.getAttributeHandle(mostHandle,"czyPusty");
        this.mostKierunekHandle = rtiamb.getAttributeHandle(mostHandle,"kierunek");

        AttributeHandleSet mostAttributes = rtiamb.getAttributeHandleSetFactory().create();
        mostAttributes.add(mostCzyPelnyHandle);
        mostAttributes.add(mostCzyPustyHandle);
        mostAttributes.add(mostKierunekHandle);

        rtiamb.subscribeObjectClassAttributes(mostHandle, mostAttributes);
        log("Most Subscription Set");
    }

    private void subscribeAuto() throws RTIexception {
        this.autoHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Samochod");
        this.autoIdHandle = rtiamb.getAttributeHandle(autoHandle,"id");
        this.autoPredkoscDrogaHandle = rtiamb.getAttributeHandle(autoHandle, "vDroga");
        this.autoPredkoscMostHandle = rtiamb.getAttributeHandle(autoHandle, "vMost");
        this.autoDrogaHandle = rtiamb.getAttributeHandle(autoHandle,"sPrzebyta");

        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(autoIdHandle);
        attributes.add(autoPredkoscDrogaHandle);
        attributes.add(autoPredkoscMostHandle);
        attributes.add(autoDrogaHandle);

        rtiamb.subscribeObjectClassAttributes(autoHandle, attributes);
        log("Samochod Subscription Set");
    }

    private void publishWjazdNaMost() throws RTIexception {
        this.wjazdNaMostHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.wjazdPojadu");
        this.wjazdAutoIdHandle = rtiamb.getParameterHandle(wjazdNaMostHandle, "idSamochodu");
        this.wjazdPredkoscAutaHandle = rtiamb.getParameterHandle(wjazdNaMostHandle, "vMost");
        rtiamb.publishInteractionClass(wjazdNaMostHandle);
        log("Wjazd na Most Publishing Set");
    }

    private void publishOpuszczenieKolejki() throws RTIexception {
        this.opuszczenieKolejkiHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.opuszczenieKolejki");
        this.opuszczenieAutoIdHandle = rtiamb.getParameterHandle(opuszczenieKolejkiHandle, "idSamochodu");
        rtiamb.publishInteractionClass(opuszczenieKolejkiHandle);
        log("Opuszczenie kolejki Publishing Set");
    }

    private void subscribeDolaczenieDoKolejki() throws RTIexception {
        this.dolaczenieDoKolejkiHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.dolaczenieDoKolejki");
        this.dolaczaAutoIdHandle = rtiamb.getParameterHandle(dolaczenieDoKolejkiHandle, "idSamochodu");
        rtiamb.subscribeInteractionClass(dolaczenieDoKolejkiHandle);
        log("Dolaczenie do kolejki Subscription Set");
    }

    private void sendInteractionWjazdNaMost(int autoId) throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);

        HLAinteger32LE auto = encoderFactory.createHLAinteger32LE(autoId);
        parameters.put(wjazdAutoIdHandle, auto.toByteArray());

        rtiamb.sendInteraction(wjazdNaMostHandle, parameters, generateTag(), time);
        log("Wysłanie interakcji wjazd na most samochodu id: " + autoId);
    }

    private void sendInteractionOpuszczenieKolejki(int autoId) throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);

        HLAinteger32LE auto = encoderFactory.createHLAinteger32LE(autoId);
        parameters.put(opuszczenieAutoIdHandle, auto.toByteArray());

        rtiamb.sendInteraction(opuszczenieKolejkiHandle, parameters, generateTag(), time);
        log("Wysłanie interakcji opuszczenie kolejki samochodu id: " + autoId);
    }

    private void updateStanKolejki(ObjectInstanceHandle objectClassHandle, int kolejkaId) throws RTIexception{
        AttributeHandleValueMap kolejkaAttributes = rtiamb.getAttributeHandleValueMapFactory().create(2);

        StringBuilder builder = new StringBuilder( "Zktualizacja stanu kolejki " );

        HLAinteger32LE idKolejka = encoderFactory.createHLAinteger32LE(kolejkaId);
        HLAinteger32LE idPierwszy;

        kolejkaAttributes.put( kolejkaNumerHandle, idKolejka.toByteArray());
        builder.append(kolejkaId);

        if(zwrocKolejkeoId(kolejkaId).getKolejkaSamochod().size() > 0 ){
            idPierwszy = encoderFactory.createHLAinteger32LE(zwrocKolejkeoId(kolejkaId).pierwszeAuto().getIdSamochod());
            builder.append(", pierwszym elementem jest samochod o id " + idPierwszy.getValue());
        }else {
            builder.append(", kolejka jest pusta");
        }

        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.updateAttributeValues( objectClassHandle, kolejkaAttributes, generateTag(), time );

        log(builder.toString());
    }

    protected void dodajDoKolejki(int autoId){
        int tmp = (int) ( Math.random() * 2 + 1); // will return either 1 or 2

        Samochod noweAuto = new Samochod(autoId);

        if(tmp == 1){
            this.kolejkaMiastoA.addAuto(noweAuto);
            log("Samochod ("+autoId+") czeka na przejazd w kolejce z Miasta A");
        }else {
            this.kolejkaMiastoB.addAuto(noweAuto);
            log("Samochod ("+autoId+") czeka na przejazd w kolejce z Miasta B");
        }
    }

    private Kolejka zwrocKolejkeoId(int kolejkaId){
        if(kolejkaMiastoA.getIdKolejki().equals(kolejkaId)){
            return kolejkaMiastoA;
        }else {
            return kolejkaMiastoB;
        }
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(kolejkaHandle);
    }

    private void advanceTime(double timestep) throws RTIexception {
        // request the advance
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);

        // wait for the time advance to be granted. ticking will tell the
        // LRC to start delivering callbacks to the federate
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

    }

    private void deleteObject(ObjectInstanceHandle handle) throws RTIexception {
        rtiamb.deleteObjectInstance(handle, generateTag());
    }

    private short getTimeAsShort() {
        return (short) fedamb.federateTime;
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    //----------------------------------------------------------
    //                     STATIC METHODS
    //----------------------------------------------------------
    public static void main(String[] args) {
        // get a federate name, use "exampleFederate" as default
        String federateName = "kolejkaFederate";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            // run the example federate
            new KolejkaFederat().runFederate(federateName);
        } catch (Exception rtie) {
            // an exception occurred, just log the information and exit
            rtie.printStackTrace();
        }
    }
}