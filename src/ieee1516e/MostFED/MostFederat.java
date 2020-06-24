package ieee1516e.MostFED;

import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import ieee1516e.Most;
import ieee1516e.Samochod;
import ieee1516e.StanSwiatel;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.Random;


public class MostFederat {
    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------
    public static final String READY_TO_RUN = "ReadyToRun";

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private RTIambassador rtiamb;
    private MostFederatAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;

    protected ObjectClassHandle mostHandle;
    protected AttributeHandle mostCzyPustyHandle;
    protected AttributeHandle mostCzyPelnyHandle;
    protected AttributeHandle mostKierunekHandle;

    protected InteractionClassHandle zmianaSwiatelHandle;
    protected ParameterHandle stanSwiatelHandle;

    protected InteractionClassHandle wjazdNaMostHandle;
    protected ParameterHandle wjazdAutoIdHandle;
    protected ParameterHandle wjazdPredkoscAutaHandle;

    protected InteractionClassHandle zjazdZMostuHandle;
    protected ParameterHandle zjazdAutoIdHandle;
    protected ParameterHandle zjazdPredkoscAutaHandle;

    protected Most most;
    private Random generator;

    //----------------------------------------------------------
    //                      CONSTRUCTORS
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                    INSTANCE METHODS
    //----------------------------------------------------------
    private void log(String message) {
        System.out.println("MostFederate   : " + message);
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
        fedamb = new MostFederatAmbassador(this);
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
        ////////////////////////////
        // 4. join the federation //
        ////////////////////////////

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

        subscribeZmianaSwiatla();
        subscribeWjazdNaMost();
        publishMost();
        publishZjazdZMostu();
        log("Published and Subscribed");

        ObjectInstanceHandle objMostHandle = rtiamb.registerObjectInstance(mostHandle);

        most = new Most();
        generator=new Random();

        while (fedamb.isRunning){
            updateStanMostu(objMostHandle, most.getStanSwiatel());

            if(generator.nextInt(5) >= 2.5) {
                if (most.getSamochodyNaMoscie().size() > 0) {
                    sendInteractionZjazdZMostu();
                }
            }

            advanceTime(1.0);
            log("Time Advanced to " + fedamb.federateTime);
        }

        deleteObject(objMostHandle);

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

    private void publishMost() throws RTIexception {
        this.mostHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Most");
        this.mostCzyPelnyHandle = rtiamb.getAttributeHandle(mostHandle, "czyPelny");
        this.mostCzyPustyHandle = rtiamb.getAttributeHandle(mostHandle,"czyPusty");
        this.mostKierunekHandle = rtiamb.getAttributeHandle(mostHandle,"kierunek");

        AttributeHandleSet mostAttributes = rtiamb.getAttributeHandleSetFactory().create();
        mostAttributes.add(mostCzyPelnyHandle);
        mostAttributes.add(mostCzyPustyHandle);
        mostAttributes.add(mostKierunekHandle);

        rtiamb.publishObjectClassAttributes(mostHandle, mostAttributes);

        log("Most Publishing Set");
    }

    private void subscribeZmianaSwiatla() throws RTIexception {
        this.zmianaSwiatelHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.zmianaSwiatel");
        this.stanSwiatelHandle = rtiamb.getParameterHandle(zmianaSwiatelHandle, "stanSwiatel");

        rtiamb.subscribeInteractionClass(zmianaSwiatelHandle);
        log("Zmiana Swiatel Subscription Set");
    }

    private void subscribeWjazdNaMost() throws RTIexception {
        this.wjazdNaMostHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.wjazdPojadu");
        this.wjazdAutoIdHandle = rtiamb.getParameterHandle(wjazdNaMostHandle, "idSamochodu");
        this.wjazdPredkoscAutaHandle = rtiamb.getParameterHandle(wjazdNaMostHandle, "vDroga");

        rtiamb.subscribeInteractionClass(wjazdNaMostHandle);
        log("Wjazd na Most Subscription Set");
    }

    private void publishZjazdZMostu() throws RTIexception {
        this.zjazdZMostuHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.zjazdPojazdu");
        this.zjazdAutoIdHandle = rtiamb.getParameterHandle(zjazdZMostuHandle, "idSamochodu");
        this.zjazdPredkoscAutaHandle = rtiamb.getParameterHandle(zjazdZMostuHandle, "vMost");

        rtiamb.publishInteractionClass(zjazdZMostuHandle);
        log("Wjazd z Mostu Publishing Set");
    }

    private void sendInteractionZjazdZMostu() throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(2);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);

        Random r = new Random();
        float ran = 1 + (most.pierwszeAuto().getvDroga() - 1)*r.nextFloat();
        most.pierwszeAuto().setvMost(most.pierwszeAuto().getvDroga() - ran);

        HLAinteger32LE auto = encoderFactory.createHLAinteger32LE(most.pierwszeAuto().getIdSamochod());
        HLAfloat32LE vMost = encoderFactory.createHLAfloat32LE(most.pierwszeAuto().getvMost());

        parameters.put(zjazdAutoIdHandle, auto.toByteArray());
        parameters.put(zjazdPredkoscAutaHandle, vMost.toByteArray());

        rtiamb.sendInteraction(zjazdZMostuHandle, parameters, generateTag(), time);

        log("Wysłanie interakcji zjazd z mostu samochodu id: " + most.pierwszeAuto().getIdSamochod());

        zjazdSamochodu(most.pierwszeAuto());
    }

    private void updateStanMostu(ObjectInstanceHandle objectClassHandle, StanSwiatel aktulanyStan) throws RTIexception{
        AttributeHandleValueMap mostAttributes = rtiamb.getAttributeHandleValueMapFactory().create(1);

        HLAASCIIstring mostKierunek = encoderFactory.createHLAASCIIstring(aktulanyStan.toString());
        HLAboolean czyPusty = encoderFactory.createHLAboolean(most.czyJestPusty());
        HLAboolean czyPelny = encoderFactory.createHLAboolean(most.czyJestPelny());

        mostAttributes.put( mostKierunekHandle, mostKierunek.toByteArray());
        mostAttributes.put( mostCzyPustyHandle, czyPusty.toByteArray());
        mostAttributes.put( mostCzyPelnyHandle, czyPelny.toByteArray());

        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.updateAttributeValues( objectClassHandle, mostAttributes, generateTag(), time );

        if(aktulanyStan.equals(StanSwiatel.CZERWONY)){
            log("Aktualny stan: zielone swiatlo dla Miasta A, most jest pusty ("+  most.czyJestPusty() + ") i most jest pelny (" + most.czyJestPelny()+")");
        }else{
            log("Aktualny stan: zielone swiatlo dla Miasta B, most jest pusty ("+  most.czyJestPusty() + ") i most jest pelny (" + most.czyJestPelny()+")");
        }
    }

    public void zmianaSwiatla(String stan){
        this.most.setStanSwiatel(StanSwiatel.valueOf(stan));

        log("Zmiana swiatel: " + stan);
    }

    public void wjazdSamochodu(int autoId, float prd){
        Samochod noweAuto = new Samochod(autoId);
        noweAuto.setvDroga(prd);

        this.most.dodajPrzejezdzajacySamochod(noweAuto);
        log("Samochod ("+autoId+") przejezdza przez most");
    }

    public void zjazdSamochodu(Samochod samochod){
        this.most.usunPrzejezdzajacySamochod(samochod);

        log("Samochod ("+samochod.getIdSamochod()+") zjechał z mostu");
    }

    /**
     * This method will register an instance of the Soda class and will
     * return the federation-wide unique handle for that instance. Later in the
     * simulation, we will update the attribute values for this instance
     */
    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(mostHandle);
    }

    /**
     * This method will request a time advance to the current time, plus the given
     * timestep. It will then wait until a notification of the time advance grant
     * has been received.
     */
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
        String federateName = "mostFederate";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            new ieee1516e.MostFED.MostFederat().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }
}