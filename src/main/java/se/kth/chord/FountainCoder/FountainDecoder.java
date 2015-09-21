package se.kth.chord.FountainCoder;

import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.Parsed;
import net.fec.openrq.decoder.DataDecoder;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Created by joakim on 2015-09-20.
 */
public class FountainDecoder {
    FECParameters parameters;
    Semaphore availableBytes;
    ConcurrentLinkedQueue<byte[]> byteQueue;
    public FountainDecoder(Collection<byte[]> bytesToDecode, FECParameters parameters){
        this.parameters = parameters;
        this.availableBytes = new Semaphore(bytesToDecode.size(),true);
        this.byteQueue = new ConcurrentLinkedQueue<>(bytesToDecode);
    }

    public FountainDecoder(ConcurrentLinkedQueue<byte[]> byteQueue, Semaphore availableBytes, FECParameters parameters){
        this.parameters = parameters;
        this.availableBytes = availableBytes;
        this.byteQueue = byteQueue;
    }
    public void startDecoding(){
        DataDecoder decoder = OpenRQ.newDecoder(parameters,1); // 0 = 99% probability of success, 1 = 99.99% probability, 2 = 99.9999% probability
        Iterator<SourceBlockDecoder> sourceIterator = decoder.sourceBlockIterable().iterator();
        int counter = 0;
        while(sourceIterator.hasNext()){
            SourceBlockDecoder sourceBlock = sourceIterator.next();
            try {
                availableBytes.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int byteQueueLength = byteQueue.size();
            int i = 0;
            while(byteQueue.peek() != null && i<byteQueueLength) {
                byte[] block = byteQueue.poll();
                i++;
                Parsed<EncodingPacket> parsedPacket = decoder.parsePacket(block, false);
                if(parsedPacket.failureReason().length() < 1){
                    try {
                        sourceBlock.putEncodingPacket(parsedPacket.value());
                        counter++;
                        if (sourceBlock.isSourceBlockDecoded()) {
                            System.out.println("The sourceBlock is decoded. It used " + counter + " droplets");
                            break;
                        }
                    }
                    catch (IllegalArgumentException e){
                        //(If it doesn't belong to this packet we throw it back.
                        byteQueue.add(block);
                    }
                }
            }
        }
        if(decoder.isDataDecoded()){
            System.out.println("The whole file was successfully recovered");
        }
    }
}
