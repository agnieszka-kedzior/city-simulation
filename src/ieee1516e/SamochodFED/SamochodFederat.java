package ieee1516e.SamochodFED;

import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import ieee1516e.StanSwiatel;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.Random;

public class SamochodFederat {
    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    public static final int ITERATIONS = 20;
    public static final String READY_TO_RUN = "ReadyToRun";
    public static final int SAMOCHOD_NUM = 10;

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private RTIambassador rtiamb;
    private SamochodFederatAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;     // set when we join

    protected ObjectClassHandle autoHandle;
    protected AttributeHandle autoIdHandle;
    protected AttributeHandle autoPredkoscDrogaHandle;
    protected AttributeHandle autoPredkoscMostHandle;
    protected AttributeHandle autoDrogaHandle;

    protected InteractionClassHandle dolaczenieDoKolejkiHandle;
    protected ParameterHandle dolaczaAutoIdHandle;
    protected InteractionClassHandle opuszczenieKolejkiHandle;
    protected ParameterHandle opuszczaAutoIdHandle;

    private Random generator;
    private int samCount;

    //----------------------------------------------------------
    //                      CONSTRUCTORS
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                    INSTANCE METHODS
    //----------------------------------------------------------

    private void log(String message) {
        System.out.println("SamochodFederate   : " + message);
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

    ///////////////////////////////////////////////////////////////////////////
    ////////////////////////// Main Simulation Method /////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void runFederate(String federateName) throws Exception {
        /////////////////////////////////////////////////
        // 1 & 2. create the RTIambassador and Connect //
        /////////////////////////////////////////////////
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new SamochodFederatAmbassador(this);
        rtiamb.connect(fedamb, CallbackModel.HLA_EVOKED);

        //////////////////////////////
        // 3. create the federation //
        //////////////////////////////
        log("Creating Federation...");
        // We attempt to create a new federation with the first three of the
        // restaurant FOM modules covering processes, food and drink
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

        rtiamb.joinFederationExecution( federateName,            // name for the federate
                "FederateType",   // federate type
                "Federation",     // name of federation
                joinModules );           // modules we want to add

        log("Joined Federation as " + federateName);

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();

        ////////////////////////////////
        // 5. announce the sync point //
        ////////////////////////////////
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

        //////////////////////////////
        // 8. publish and subscribe //
        //////////////////////////////
        publishSamochod();
        publishDolaczenieDoKolejki();
        publishOpuszczenieKolejki();
        log("Published and Subscribed");

        ObjectInstanceHandle objAutoHandle = rtiamb.registerObjectInstance(autoHandle);
        log( "Registered Object, handle=" + objAutoHandle );

        samCount = SAMOCHOD_NUM;

        while (fedamb.isRunning){
            samCount--;
            if(samCount<0) {
                sendInteractionDolaczenieDoKolejki(samCount);
            }
            //updateSamochodAttributeValues(objAutoHandle);

            advanceTime( 1.0);
            log( "Time Advanced to " + fedamb.federateTime );
        }

        deleteObject(objAutoHandle);
        log("Deleted Object, handle=" + objAutoHandle);

        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        log("Resigned from Federation");

        try {
            rtiamb.destroyFederationExecution("SamochodFederation");
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

    private void publishSamochod() throws RTIexception {
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

        rtiamb.publishObjectClassAttributes(autoHandle, attributes);

        log("Samochod Publishing Set");
    }

    private void publishDolaczenieDoKolejki() throws RTIexception {
        this.dolaczenieDoKolejkiHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.dolaczenieDoKolejki");
        this.dolaczaAutoIdHandle = rtiamb.getParameterHandle(dolaczenieDoKolejkiHandle, "idSamochodu");
        rtiamb.publishInteractionClass(dolaczenieDoKolejkiHandle);
    }

    private void publishOpuszczenieKolejki() throws RTIexception {
        this.dolaczenieDoKolejkiHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.opuszczenieKolejki");
        this.dolaczaAutoIdHandle = rtiamb.getParameterHandle(dolaczenieDoKolejkiHandle, "idSamochodu");
        rtiamb.publishInteractionClass(dolaczenieDoKolejkiHandle);
    }

    private void sendInteractionDolaczenieDoKolejki(int autoId) throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);

        HLAinteger32LE auto = encoderFactory.createHLAinteger32LE(autoId);
        parameters.put(dolaczaAutoIdHandle, auto.toByteArray());

        rtiamb.sendInteraction(dolaczenieDoKolejkiHandle, parameters, generateTag(), time);
        log("Wysłanie interakcji dolaczenie do kolejki samochodu "+autoId);
    }

    private void sendInteractionOpuszczenieKolejki(int autoId) throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(2);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);

        HLAinteger32LE auto = encoderFactory.createHLAinteger32LE(autoId);
        parameters.put(opuszczaAutoIdHandle, auto.toByteArray());

        rtiamb.sendInteraction(opuszczenieKolejkiHandle, parameters, generateTag(), time);
        log("Wysłanie interakcji opuszczenie kolejki");
    }

    private void updateSamochodAttributeValues( ObjectInstanceHandle objectHandle ) throws RTIexception
    {
        AttributeHandleValueMap autoAttributes = rtiamb.getAttributeHandleValueMapFactory().create(1);

        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.updateAttributeValues( objectHandle, autoAttributes, generateTag(), time );

        log("Aktualizacja atrybutów obiektu samochodu ");
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(autoHandle);
    }

    private void updateAttributeValues(ObjectInstanceHandle objectHandle) throws RTIexception {
        ///////////////////////////////////////////////
        // create the necessary container and values //
        ///////////////////////////////////////////////
        // create a new map with an initial capacity - this will grow as required
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(2);

        // create the collection to store the values in, as you can see
        // this is quite a lot of work. You don't have to use the encoding
        // helpers if you don't want. The RTI just wants an arbitrary byte[]

        // generate the value for the number of cups (same as the timestep)
        HLAinteger16BE cupsValue = encoderFactory.createHLAinteger16BE(getTimeAsShort());
        //attributes.put(cupsHandle, cupsValue.toByteArray());

        // generate the value for the flavour on our magically flavour changing drink
        // the values for the enum are defined in the FOM
        int randomValue = 101 + new Random().nextInt(3);
        HLAinteger32BE flavValue = encoderFactory.createHLAinteger32BE(randomValue);
        //attributes.put(flavHandle, flavValue.toByteArray());

        //////////////////////////
        // do the actual update //
        //////////////////////////
        rtiamb.updateAttributeValues(objectHandle, attributes, generateTag());

        // note that if you want to associate a particular timestamp with the
        // update. here we send another update, this time with a timestamp:
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
        rtiamb.updateAttributeValues(objectHandle, attributes, generateTag(), time);
    }

    private void sendInteraction() throws RTIexception {

        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);

        rtiamb.sendInteraction(dolaczenieDoKolejkiHandle, parameters, generateTag(), time);
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
        String federateName = "samochodFederate";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            new SamochodFederat().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }
}