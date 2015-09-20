package se.kth.chord.FountainCoder;

import net.fec.openrq.OpenRQ;
import net.fec.openrq.decoder.DataDecoder;
import net.fec.openrq.parameters.FECParameters;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Created by joakim on 2015-09-20.
 */
public class FountainDecoder {
    FECParameters parameters;
    public FountainDecoder(Collection<byte[]> bytesToDecode, FECParameters parameters){
        this.parameters = parameters;
    }
    public FountainDecoder(ConcurrentLinkedQueue<byte[]> byteQueue, Semaphore availableBytes, FECParameters parameters){
        this.parameters = parameters;
    }
    public void startDecoding(){
        DataDecoder decoder = OpenRQ.newDecoder(parameters,0);


    }
}
