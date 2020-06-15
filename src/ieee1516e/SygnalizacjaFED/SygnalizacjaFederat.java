package ieee1516e.SygnalizacjaFED;

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

public class SygnalizacjaFederat {
    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    public static final int ITERATIONS = 20;
    public static final String READY_TO_RUN = "ReadyToRun";

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private RTIambassador rtiamb;
    private SygnalizacjaFederatAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;     // set when we join

    protected InteractionClassHandle zmianaSwiatelHandle;
    protected ParameterHandle stanSwiatelHandle;

    protected ObjectClassHandle mostHandle;
    protected AttributeHandle mostKierunekHandle;

    //----------------------------------------------------------
    //                      CONSTRUCTORS
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                    INSTANCE METHODS
    //----------------------------------------------------------

    private void log(String message) {
        System.out.println("SygnalizacjaFederate   : " + message);
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
        fedamb = new SygnalizacjaFederatAmbassador(this);
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

        URL[] joinModules = new URL[]{
                (new File("foms/HLA.xml")).toURI().toURL()
        };

        rtiamb.joinFederationExecution(federateName,
                "FederateType",
                "Federation",
                joinModules);

        log("Joined Federation as " + federateName);

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

        subscribeMost();
        publishSwiatla();
        log("Published and Subscribed");

        while (fedamb.isRunning) {
            // 9.1 update the attribute values of the instance //
            //updateAttributeValues(objectHandle);

            // 9.2 send an interaction
            sendInteractionZmianaSwiatel();

            // 9.3 request a time advance and wait until we get it
            advanceTime(1.0);
            log("Time Advanced to " + fedamb.federateTime);
        }

        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        log("Resigned from Federation");

        try {
            rtiamb.destroyFederationExecution("SygnalizacjaFederation");
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

    private void subscribeMost() throws RTIexception {
        this.mostHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Most");
        this.mostKierunekHandle = rtiamb.getAttributeHandle(mostHandle,"kierunek");

        AttributeHandleSet mostAttributes = rtiamb.getAttributeHandleSetFactory().create();
        mostAttributes.add(mostKierunekHandle);

        rtiamb.subscribeObjectClassAttributes(mostHandle, mostAttributes);
        log("Most Subscription Set");
    }


    private void publishSwiatla() throws RTIexception {
        this.zmianaSwiatelHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.zmianaSwiatel");
        this.stanSwiatelHandle = rtiamb.getParameterHandle(zmianaSwiatelHandle, "stanSwiatel");

        rtiamb.publishInteractionClass(zmianaSwiatelHandle);
    }

    private void sendInteractionZmianaSwiatel() throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);

        System.out.println("Kierunek most? "+ this.mostKierunekHandle);

        HLAASCIIstring stanSwiatel = encoderFactory.createHLAASCIIstring(StanSwiatel.CZERWONY.toString());
        parameters.put(stanSwiatelHandle, stanSwiatel.toByteArray());

        rtiamb.sendInteraction(zmianaSwiatelHandle, parameters, generateTag(), time);
        log("Wysłanie interakcji zmiana swiatel");
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
        String federateName = "sygnalizacjaFederate";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            new ieee1516e.SygnalizacjaFED.SygnalizacjaFederat().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }
}