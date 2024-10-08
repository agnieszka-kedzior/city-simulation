package ieee1516e.KolejkaFED;

import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import ieee1516e.StanSwiatel;
import org.portico.impl.hla1516e.types.encoding.HLA1516eFloat32LE;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32BE;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32LE;


public class KolejkaFederatAmbassador extends NullFederateAmbassador {
    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private KolejkaFederat federate;

    // these variables are accessible in the package
    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;

    protected boolean isRunning = true;

    protected ObjectInstanceHandle autoHandle = new ObjectInstanceHandle() {
        @Override
        public int encodedLength() {
            return 0;
        }

        @Override
        public void encode(byte[] bytes, int i) {

        }
    };
    protected ObjectInstanceHandle mostHandle = new ObjectInstanceHandle() {
        @Override
        public int encodedLength() {
            return 0;
        }

        @Override
        public void encode(byte[] bytes, int i) {
        }
    };
            //----------------------------------------------------------
    //                      CONSTRUCTORS
    //----------------------------------------------------------

    public KolejkaFederatAmbassador(KolejkaFederat federate) {
        this.federate = federate;
    }

    //----------------------------------------------------------
    //                    INSTANCE METHODS
    //----------------------------------------------------------
    private void log(String message) {
        System.out.println("FederateAmbassador: " + message);
    }

    //////////////////////////////////////////////////////////////////////////
    ////////////////////////// RTI Callback Methods //////////////////////////
    //////////////////////////////////////////////////////////////////////////
    @Override
    public void synchronizationPointRegistrationFailed(String label,
                                                       SynchronizationPointFailureReason reason) {
        log("Failed to register sync point: " + label + ", reason=" + reason);
    }

    @Override
    public void synchronizationPointRegistrationSucceeded(String label) {
        log("Successfully registered sync point: " + label);
    }

    @Override
    public void announceSynchronizationPoint(String label, byte[] tag) {
        log("Synchronization point announced: " + label);
        if (label.equals(KolejkaFederat.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(KolejkaFederat.READY_TO_RUN))
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
    @Override
    public void timeRegulationEnabled(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isRegulating = true;
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isConstrained = true;
    }

    @Override
    public void timeAdvanceGrant(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isAdvancing = false;
    }

    @Override
    public void discoverObjectInstance(ObjectInstanceHandle theObject,
                                       ObjectClassHandle theObjectClass,
                                       String objectName)
            throws FederateInternalError {
        if(theObjectClass.equals(federate.mostHandle)) mostHandle = theObject;
        log("Discoverd Object: handle=" + theObject + ", classHandle=" +
                theObjectClass + ", name=" + objectName);
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] tag,
                                       OrderType sentOrder,
                                       TransportationTypeHandle transport,
                                       SupplementalReflectInfo reflectInfo)
            throws FederateInternalError {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        reflectAttributeValues(theObject,
                theAttributes,
                tag,
                sentOrder,
                transport,
                null,
                sentOrder,
                reflectInfo);
    }

    @Override
    public void reflectAttributeValues( ObjectInstanceHandle theObject,
                                        AttributeHandleValueMap theAttributes,
                                        byte[] tag,
                                        OrderType sentOrdering,
                                        TransportationTypeHandle theTransport,
                                        LogicalTime time,
                                        OrderType receivedOrdering,
                                        SupplementalReflectInfo reflectInfo )
            throws FederateInternalError
    {
        StringBuilder builder = new StringBuilder( "Reflection for object:" );

        builder.append( " handle=" + theObject );
        builder.append( ", tag=" + new String(tag) );
        if( time != null )
        {
            builder.append( ", time=" + ((HLAfloat64Time)time).getValue() );
        }


        builder.append( ", attributeCount=" + theAttributes.size() );

        HLAASCIIstring kierunekMostu = federate.encoderFactory.createHLAASCIIstring();
        HLAboolean czyPusty = federate.encoderFactory.createHLAboolean();
        HLAboolean czyPelny = federate.encoderFactory.createHLAboolean();

        for( AttributeHandle attributeHandle : theAttributes.keySet() )
        {
            builder.append( "\nattributeHandle=" );

            if(attributeHandle.equals(federate.mostCzyPustyHandle)){
                builder.append( attributeHandle );
                builder.append( " (Most)" );
                try {
                    czyPusty.decode(theAttributes.get(attributeHandle));
                    builder.append( " czy pusty: " + czyPusty.getValue());
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
            }
            if(attributeHandle.equals(federate.mostCzyPelnyHandle)){
                builder.append( attributeHandle );
                builder.append( " (Most)" );
                try {
                    czyPelny.decode(theAttributes.get(attributeHandle));
                    builder.append( " czy pelny: " + czyPelny.getValue());
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
            }
            if(attributeHandle.equals(federate.mostKierunekHandle)){
                builder.append( attributeHandle );
                builder.append( " (Most)" );
                try {
                    kierunekMostu.decode(theAttributes.get(attributeHandle));
                    builder.append( " stan: " + kierunekMostu.getValue());
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
            }
        }

        //log( builder.toString() );

        if (theObject.equals(mostHandle)) {
            if(kierunekMostu.getValue().contentEquals(StanSwiatel.CZERWONY.toString())){
                log("Zielone swiatlo dla Miasta A");
                federate.przejazdDlaMiasta = "A";

                if(federate.wjazdNaMostMiastoB) {
                    if(czyPusty.getValue()){
                        federate.wjazdNaMostMiastoB = false;

                        if(!czyPelny.getValue()){
                            federate.wjazdNaMostMiastoA = true;
                        }else {
                            federate.wjazdNaMostMiastoA = false;
                        }
                    }
                }else {
                    if(!czyPelny.getValue()){
                        federate.wjazdNaMostMiastoA = true;
                    }else {
                        federate.wjazdNaMostMiastoA = false;
                    }
                }

            }else if(kierunekMostu.getValue().contentEquals(StanSwiatel.ZIELONY.toString())) {
                log("Zielone swiatlo dla Miasta B");
                federate.przejazdDlaMiasta = "B";

                if (federate.wjazdNaMostMiastoA){
                    if(czyPusty.getValue()){
                        federate.wjazdNaMostMiastoA = false;

                        if(!czyPelny.getValue()){
                            federate.wjazdNaMostMiastoB = true;
                        }else {
                            federate.wjazdNaMostMiastoB = false;
                        }
                    }
                }else {
                    if(!czyPelny.getValue()){
                        federate.wjazdNaMostMiastoB = true;
                    }else {
                        federate.wjazdNaMostMiastoB = false;
                    }
                }
            }
        }
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {

    }

    @Override
    public void receiveInteraction( InteractionClassHandle interactionClass,
                                    ParameterHandleValueMap theParameters,
                                    byte[] tag,
                                    OrderType sentOrdering,
                                    TransportationTypeHandle theTransport,
                                    LogicalTime time,
                                    OrderType receivedOrdering,
                                    SupplementalReceiveInfo receiveInfo )
            throws FederateInternalError
    {
        if(interactionClass.equals(federate.dolaczenieDoKolejkiHandle)){
            HLAinteger32LE autoId = new HLA1516eInteger32LE();
            HLAfloat32LE vDroga = new HLA1516eFloat32LE();

            byte[] bytes = theParameters.get(federate.dolaczaAutoIdHandle);
            try {
                autoId.decode(bytes);
            } catch (DecoderException e) {
                e.printStackTrace();
            }

            byte[] bytesV = theParameters.get(federate.dolaczaAutoVdrogaHandle);
            try {
                vDroga.decode(bytesV);
            } catch (DecoderException e) {
                e.printStackTrace();
            }

            log("Otrzymana została interakcja dolaczenie do kolejki samochodu id: " + autoId.getValue()+", o predkosci "+vDroga.getValue());

            federate.dodajDoKolejki(autoId.getValue(), vDroga.getValue());
        }else if(interactionClass.equals(federate.zakonczHandle)){
            log("Zakonczenie symulacji");
            isRunning = false;
        }

    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] tag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo)
            throws FederateInternalError {
        log("Object Removed: handle=" + theObject);
    }

    //----------------------------------------------------------
    //                     STATIC METHODS
    //----------------------------------------------------------
}
