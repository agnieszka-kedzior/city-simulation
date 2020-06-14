package ieee1516e.SamochodFED;

import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger16BE;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;


import hla.rti1516e.AttributeHandle;
import hla.rti1516e.AttributeHandleSet;
import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.CallbackModel;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.ResignAction;
import hla.rti1516e.RtiFactoryFactory;

public class SamochodFederat {
    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    public static final int ITERATIONS = 20;
    public static final String READY_TO_RUN = "ReadyToRun";

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
        log("Published and Subscribed");

        /////////////////////////////////////
        // 9. register an object to update //
        /////////////////////////////////////
        ObjectInstanceHandle objAutoHandle = rtiamb.registerObjectInstance(autoHandle);

        /////////////////////////////////////
        // 10. do the main simulation loop //
        /////////////////////////////////////
        while(fedamb.isRunning){
            // 9.1 update the attribute values of the instance //
            //updateAttributeValues(objectHandle);

            // 9.2 send an interaction
            //sendInteraction();

            // 9.3 request a time advance and wait until we get it
            advanceTime(1.0);
            log("Time Advanced to " + fedamb.federateTime);
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

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// Helper Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * This method will attempt to enable the various time related properties for
     * the federate
     */
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

    /**
     * This method will inform the RTI about the types of data that the federate will
     * be creating, and the types of data we are interested in hearing about as other
     * federates produce it.
     */
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

    /**
     * This method will send out an interaction of the type FoodServed.DrinkServed. Any
     * federates which are subscribed to it will receive a notification the next time
     * they tick(). This particular interaction has no parameters, so you pass an empty
     * map, but the process of encoding them is the same as for attributes.
     */
    private void sendInteraction() throws RTIexception {
        //////////////////////////
        // send the interaction //
        //////////////////////////
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        //rtiamb.sendInteraction(servedHandle, parameters, generateTag());

        // if you want to associate a particular timestamp with the
        // interaction, you will have to supply it to the RTI. Here
        // we send another interaction, this time with a timestamp:
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
        //rtiamb.sendInteraction(servedHandle, parameters, generateTag(), time);
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

    /**
     * This method will attempt to delete the object instance of the given
     * handle. We can only delete objects we created, or for which we own the
     * privilegeToDelete attribute.
     */
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