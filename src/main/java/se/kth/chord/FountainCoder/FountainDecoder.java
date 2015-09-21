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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
        DataDecoder decoder = OpenRQ.newDecoder(parameters,1); // 0 = 99% probability of success, 1 = 99.99% probability, 2 = 99.9999% probability. This is only for performance reasons.
        Iterator<SourceBlockDecoder> sourceIterator = decoder.sourceBlockIterable().iterator();
        int nrOfThreads = Math.min(decoder.numberOfSourceBlocks(), FountainEncoder.NR_OF_THREADS);
        BlockDecoder [] decoders = new BlockDecoder[nrOfThreads];
        ReentrantLock sourceIteratorLock = new ReentrantLock();
        ReentrantLock decoderLock = new ReentrantLock();
        for(int i = 0; i<nrOfThreads; i++){
            decoders[i] = new BlockDecoder(sourceIterator, sourceIteratorLock, byteQueue,decoder,decoderLock,i);
            decoders[i].start();
        }
        try {
            for (int i = 0; i < nrOfThreads; i++) {
                if (decoders[i] != null) {
                    decoders[i].join();
                }
            }
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(-3);
        }
        if(decoder.isDataDecoded()){
            System.out.println("The whole file was successfully recovered");
        }
        else{
            System.out.println("The file couldn't be recovered");
        }
    }
}
class BlockDecoder extends Thread {
    ReentrantLock sourceIteratorLock;
    Iterator<SourceBlockDecoder> sourceIterator;
    ConcurrentLinkedQueue<byte[]> bytesToRead;
    DataDecoder decoder;
    ReentrantLock decoderLock;
    int threadNumber;
    public void run() {
        sourceIteratorLock.lock();
        while(sourceIterator.hasNext()){
            SourceBlockDecoder nextBlock = sourceIterator.next();
            sourceIteratorLock.unlock();
            decodeSourceBlock(nextBlock);
            sourceIteratorLock.lock();
        }
        sourceIteratorLock.unlock();
        System.out.println("Decoding thread nr" + threadNumber + " has finished decoding");
    }

    public BlockDecoder(Iterator<SourceBlockDecoder> sourceIterator, ReentrantLock sourceIteratorLock, ConcurrentLinkedQueue<byte[]> bytesToRead, DataDecoder decoder, ReentrantLock decoderLock, int threadNumber) {
        this.sourceIterator = sourceIterator;
        this.sourceIteratorLock = sourceIteratorLock;
        this.bytesToRead = bytesToRead;
        this.threadNumber = threadNumber;
        this.decoder = decoder;
        this.decoderLock = decoderLock;
    }

    private void decodeSourceBlock(SourceBlockDecoder sourceBlock) {
        byte[] block;
        int counter = 0;
        while((block = bytesToRead.poll() )!= null) {
            decoderLock.lock();
            Parsed<EncodingPacket> parsedPacket = decoder.parsePacket(block, false);
            decoderLock.unlock();
            if(parsedPacket.failureReason().length() < 1){
                try {
                    sourceBlock.putEncodingPacket(parsedPacket.value());
                    counter++;
                    if (sourceBlock.isSourceBlockDecoded()) {
                        break;
                    }
                }
                catch (IllegalArgumentException e){
                    //(If it doesn't belong to this packet we throw it back.
                    bytesToRead.add(block);
                }
            }
        }
    }


}
