package se.kth.chord.FountainCoder;


import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.Parsed;
import net.fec.openrq.decoder.DataDecoder;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.parameters.FECParameters;
import se.kth.chord.node.DataBlock;

import javax.xml.crypto.Data;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by joakim on 2015-09-20.
 */
public class FountainDecoder extends Thread{
    boolean success = false;
    FECParameters parameters;
    Semaphore availableBytes;
    ConcurrentLinkedQueue<LinkedList<DataBlock>> workQueue;
    LinkedList<DataBlock> byteQueue;
    BlockDecoder [] decoders = null;
    public FountainDecoder(Collection<DataBlock> bytesToDecode, FECParameters parameters){
        this.parameters = parameters;
        this.availableBytes = new Semaphore(bytesToDecode.size(),true);
        this.byteQueue = new LinkedList<>(bytesToDecode);
    }

    public FountainDecoder(FECParameters parameters){
        this.parameters = parameters;
        this.availableBytes = new Semaphore(0, true);
        this.byteQueue = new LinkedList<>();
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
        Collections.sort(byteQueue);
        workQueue = new ConcurrentLinkedQueue<>();
        while(!byteQueue.isEmpty()){
            int newSequenceNumber = byteQueue.peek().getSourceBlockNumber()+1;
            LinkedList<DataBlock> sourceList = new LinkedList<>();
            while(!byteQueue.isEmpty() && byteQueue.peek().getSourceBlockNumber()<newSequenceNumber){
                sourceList.add(byteQueue.poll());
            }
            workQueue.add(sourceList);
        }
        decoders = new BlockDecoder[nrOfThreads];
        ReentrantLock sourceIteratorLock = new ReentrantLock();
        ReentrantLock decoderLock = new ReentrantLock();
        for(int i = 0; i<nrOfThreads; i++){
            decoders[i] = new BlockDecoder(sourceIterator, sourceIteratorLock, workQueue,decoder,decoderLock,i, availableBytes); //The threads start in the constructor
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
            //System.out.println("The whole file was successfully recovered");
            success = true;
        }
        else{
            //System.out.println("The file couldn't be recovered");
        }
    }
    public boolean success(){
        return success;
    }
    public void addDataBlock(DataBlock blockToAdd){
        this.byteQueue.add(blockToAdd);
        availableBytes.release();
    }
}
class BlockDecoder extends Thread {
    ReentrantLock sourceIteratorLock;
    Iterator<SourceBlockDecoder> sourceIterator;
    ConcurrentLinkedQueue<LinkedList<DataBlock>> bytesToRead;
    DataDecoder decoder;
    ReentrantLock decoderLock;
    Semaphore availableBytes;
    int threadNumber;
    boolean interrupted = false;
    public void run() {
        sourceIteratorLock.lock();
        while(sourceIterator.hasNext()){
            SourceBlockDecoder nextBlock = sourceIterator.next();
            LinkedList<DataBlock> nextSource = bytesToRead.poll();

            sourceIteratorLock.unlock();
            if(nextSource != null) {
                decodeSourceBlock(nextBlock, nextSource);
            }
            else{
                //System.out.println("Something went horribly wrong");
            }
            sourceIteratorLock.lock();
        }
        sourceIteratorLock.unlock();
        //System.out.println("Decoding thread nr" + threadNumber + " has finished decoding");
    }

    public BlockDecoder(Iterator<SourceBlockDecoder> sourceIterator, ReentrantLock sourceIteratorLock, ConcurrentLinkedQueue<LinkedList<DataBlock>> bytesToRead, DataDecoder decoder, ReentrantLock decoderLock, int threadNumber, Semaphore availableBytes) {
        this.sourceIterator = sourceIterator;
        this.sourceIteratorLock = sourceIteratorLock;
        this.bytesToRead = bytesToRead;
        this.threadNumber = threadNumber;
        this.decoder = decoder;
        this.decoderLock = decoderLock;
        this.availableBytes = availableBytes;
        this.start();
    }

    private void decodeSourceBlock(SourceBlockDecoder sourceBlock, LinkedList<DataBlock> nextSource) {
        while(!sourceBlock.isSourceBlockDecoded()) {
            DataBlock nextBlock  = nextSource.poll();
            if(nextSource == null || nextBlock == null){
                //System.out.println("Thread number " + threadNumber + " were unable to decode a sourceblock");
                break;
            }
            else {
                decoderLock.lock();
                Parsed<EncodingPacket> parsedPacket = decoder.parsePacket(nextBlock.getData(), false);
                decoderLock.unlock();
                if (parsedPacket.failureReason().length() < 1) {
                    try {
                        sourceBlock.putEncodingPacket(parsedPacket.value());

                    } catch (IllegalArgumentException e) {
                        //System.out.println("A packet in thread number " + threadNumber + " were sorted wrong. This is bad.");
                    }
                }
                else{
                    //System.out.println("Failure in thread nr " + threadNumber + " with message:" + parsedPacket.failureReason());
                }
            }
        }
    }


}
