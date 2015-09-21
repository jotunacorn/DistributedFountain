package se.kth.chord.FountainCoder;


import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.Parsed;
import net.fec.openrq.decoder.DataDecoder;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.parameters.FECParameters;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by joakim on 2015-09-20.
 */
public class FountainDecoder extends Thread{

    FECParameters parameters;
    Semaphore availableBytes;
    ConcurrentLinkedQueue<byte[]> byteQueue;
    BlockDecoder [] decoders = null;
    public FountainDecoder(Collection<byte[]> bytesToDecode, FECParameters parameters){
        this.parameters = parameters;
        this.availableBytes = new Semaphore(bytesToDecode.size(),true);
        this.byteQueue = new ConcurrentLinkedQueue<>(bytesToDecode);
    }

    public FountainDecoder(FECParameters parameters){
        this.parameters = parameters;
        this.availableBytes = new Semaphore(0, true);
        this.byteQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void run() {
        startDecoding();
    }
    public void setParameters(FECParameters parameters){
        this.parameters = parameters;
    }
    public void startDecoding(){
        DataDecoder decoder = OpenRQ.newDecoder(parameters,1); // 0 = 99% probability of success, 1 = 99.99% probability, 2 = 99.9999% probability. This is only for performance reasons.
        Iterator<SourceBlockDecoder> sourceIterator = decoder.sourceBlockIterable().iterator();
        int nrOfThreads = Math.min(decoder.numberOfSourceBlocks(), FountainEncoder.NR_OF_THREADS);
        decoders = new BlockDecoder[nrOfThreads];
        ReentrantLock sourceIteratorLock = new ReentrantLock();
        ReentrantLock decoderLock = new ReentrantLock();
        for(int i = 0; i<nrOfThreads; i++){
            decoders[i] = new BlockDecoder(sourceIterator, sourceIteratorLock, byteQueue,decoder,decoderLock,i, availableBytes); //The threads start in the constructor
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
    public void addBytes(byte [] arrayToAdd){
        this.byteQueue.add(arrayToAdd);
        availableBytes.release();
    }
    public void interruptDecoding(){
        if(decoders!=null){
            for(int i = 0; i<decoders.length; i++){
                decoders[i].interruptThread();
            }
            byteQueue.notifyAll();
        }
        else{
            System.out.println("Decoding hasn't started");
        }

    }
}
class BlockDecoder extends Thread {
    ReentrantLock sourceIteratorLock;
    Iterator<SourceBlockDecoder> sourceIterator;
    ConcurrentLinkedQueue<byte[]> bytesToRead;
    DataDecoder decoder;
    ReentrantLock decoderLock;
    Semaphore availableBytes;
    int threadNumber;
    boolean interrupted = false;
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

    public void interruptThread(){
        this.interrupted = true;
        this.interruptThread();
    }
    public BlockDecoder(Iterator<SourceBlockDecoder> sourceIterator, ReentrantLock sourceIteratorLock, ConcurrentLinkedQueue<byte[]> bytesToRead, DataDecoder decoder, ReentrantLock decoderLock, int threadNumber, Semaphore availableBytes) {
        this.sourceIterator = sourceIterator;
        this.sourceIteratorLock = sourceIteratorLock;
        this.bytesToRead = bytesToRead;
        this.threadNumber = threadNumber;
        this.decoder = decoder;
        this.decoderLock = decoderLock;
        this.availableBytes = availableBytes;
        this.start();
    }

    private void decodeSourceBlock(SourceBlockDecoder sourceBlock) {
        while(!sourceBlock.isSourceBlockDecoded()) {
            if(interrupted){
                System.out.println("Thread nr " + threadNumber + " has been interrupted");
                break;
            }
            byte[] block = bytesToRead.poll();
            if(block == null){
                try {
                    availableBytes.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            else {
                decoderLock.lock();
                Parsed<EncodingPacket> parsedPacket = decoder.parsePacket(block, false);
                decoderLock.unlock();
                if (parsedPacket.failureReason().length() < 1) {
                    try {
                        sourceBlock.putEncodingPacket(parsedPacket.value());

                    } catch (IllegalArgumentException e) {
                        //(If it doesn't belong to this packet we throw it back.
                        bytesToRead.add(block);
                        availableBytes.release();
                    }
                }
                else{
                    System.out.println("Failure in thread nr " + threadNumber + " with message:" + parsedPacket.failureReason());
                }
            }
        }
    }


}
