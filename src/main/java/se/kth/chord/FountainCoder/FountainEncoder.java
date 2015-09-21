package se.kth.chord.FountainCoder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.fec.openrq.*;
import net.fec.openrq.encoder.DataEncoder;
import net.fec.openrq.encoder.SourceBlockEncoder;

import static net.fec.openrq.parameters.ParameterChecker.maxAllowedDataLength;
import static net.fec.openrq.parameters.ParameterChecker.minDataLength;

import net.fec.openrq.parameters.FECParameters;

public class FountainEncoder extends Thread{


    private static final int BYTES_TO_READ = 256 * 1024 * 1024;
    private ConcurrentLinkedQueue<byte[]> result;
    private Semaphore availableDrops;
    private Semaphore done;
    private Path pathToRead = Paths.get("C:\\Users\\joakim\\Downloads\\ubuntu-14.04.3-server-i386.iso");
    private static FECParameters parameters = null;
    private int nrOfBytes = 0;

    //Encoding properties
    //Number of threads encoding
    public static final int NR_OF_THREADS = 8;
    //The number of sourceblocks needed
    private static final int NR_OF_SOURCEBLOCKS = 8; //If number of partitions is less than number of threads less threads will be used.
    // Fixed value for the symbol size
    private static final int SYMB_SIZE = 8*(1500 - 20 - 8); // X * (UDP-Ipv4 payload length)
    // The maximum allowed data length, given the parameter above
    public static final long MAX_DATA_LEN = maxAllowedDataLength(SYMB_SIZE);
    // The redundancy in the system. This number should be higher than the anticipated loss. Chance of loss also increase with NR_OF_SOURCEBLOCKS
    public static final double ESTIMATED_LOSS = 0.5;

    //A simple usage sample for receiving droplets via a queue
    public static void main(String[] args) {
        FountainEncoder fountainCoder = new FountainEncoder(Paths.get("C:\\Users\\joakim\\Downloads\\ubuntu-14.04.3-server-i386.iso"), BYTES_TO_READ); //New encoder with a Path to read
        Semaphore s = fountainCoder.dropsletsSemaphore();   //Semaphore to see if there are new droplets available
        ConcurrentLinkedQueue<byte[]> result = fountainCoder.getQueue();    //Queue with the output
        fountainCoder.start();   //Start the encoder in a new thread
        int counter = 0;        //Count the number of droplets received
        long totalSize = 0;     //Count the total size of the output
        FountainDecoder decoder = new FountainDecoder(parameters);  //Create a new decoder with the same parameters as the encoder
        boolean firstAcquire = true;
        while (result.peek()!=null) {   //Run as long as we're getting blocks
            boolean acquired = false;   //See if there are new blocks available
            try {
                acquired = s.tryAcquire(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (acquired) { //We've gotten a new block
                if(firstAcquire){    //If it's our first acquire we start up the decoder.
                    firstAcquire = false;
                    decoder.setParameters(parameters);
                    decoder.start();
                }

                byte[] block = result.poll();   //retrive the block
                totalSize = totalSize + block.length;
                if(Math.random() > 0.35) {        //Throw away some files
                    decoder.addBytes(block);      //Add the block to the decoder
                }
                counter++;
                if(counter%5000==0)
                    System.out.println("Received block nr " + counter);

            }

        }
        System.out.println("All blocks (" + counter + ") received at main. The encoded files are " + totalSize/ 1000000 + "MB");
        long time = System.currentTimeMillis();
        try {
            decoder.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Done with decoding. Time since encoding was done is " + (System.currentTimeMillis()-time) + "ms.");

    }

    public FountainEncoder(Path path, int nrOfBytes) {
        this.nrOfBytes = nrOfBytes;
        this.pathToRead = path;
        result = new ConcurrentLinkedQueue<>();
        availableDrops = new Semaphore(0, false);
        done = new Semaphore(0, false);
    }

    public ConcurrentLinkedQueue<byte[]> getQueue() {
        return result;
    }

    public Semaphore dropsletsSemaphore() {
        return availableDrops;
    }

    public Semaphore getDoneLock() {
        return this.done;
    }

    public FECParameters getParameters(){
        return parameters;
    }
    public void run() {
        System.out.println("The max length is " + MAX_DATA_LEN + " and the bytes to read is " + nrOfBytes);
        byte[] data = null;
        try {
            data = Files.readAllBytes(pathToRead);
        } catch (IOException e) {
            e.printStackTrace();
        }
        parameters = getParameters(nrOfBytes);
        System.out.println("Created parameters with \n " +
                "Symbolsize:" + parameters.symbolSize() +
                "\n Datalength:" + parameters.dataLengthAsInt() +
                "\n NumberOfSourceBlocks:" + parameters.numberOfSourceBlocks() +
                "\n TotalSymbols:" + parameters.totalSymbols());
        int nrOfRepairSymbols=OpenRQ.minRepairSymbols(parameters.totalSymbols(), 0, ESTIMATED_LOSS);
        System.out.println("The number of symbols needed to be transmitted are " + nrOfRepairSymbols + "(" + String.format("%.2f",((double)nrOfRepairSymbols/(double)parameters.totalSymbols())*100) + "%)" );
        nrOfRepairSymbols = (nrOfRepairSymbols/NR_OF_SOURCEBLOCKS)+1;
        DataEncoder encoder = OpenRQ.newEncoder(data, parameters);
        Iterator<SourceBlockEncoder> iter = encoder.sourceBlockIterable().iterator();
        System.out.println("Starting encoding process");
        long time = System.currentTimeMillis();
        BlockEncoder[] encoders = new BlockEncoder[NR_OF_THREADS];
        Lock l = new ReentrantLock();
        for (int i = 0; i < NR_OF_THREADS; i++) {
            encoders[i] = new BlockEncoder(iter, l, i, availableDrops, result, nrOfRepairSymbols);
            encoders[i].start();
        }
        try {
            for (int i = 0; i < NR_OF_THREADS; i++) {
                if (encoders[i] != null) {
                    encoders[i].join();
                }
            }
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(-3);
        }
        long finishTime = (System.currentTimeMillis() - time);
        done.release();
        System.out.println("Finished with encoding " + nrOfBytes / (1024 * 1024) + "MiB in " + finishTime + "ms. Average " + nrOfBytes/(finishTime * 1024) + " MB/s");
    }

    public static FECParameters getParameters(long dataLen) {

        if (dataLen < minDataLength())
            throw new IllegalArgumentException("data length is too small");
        if (dataLen > MAX_DATA_LEN)
            throw new IllegalArgumentException("data length is too large");

        return FECParameters.newParameters(dataLen, SYMB_SIZE, NR_OF_SOURCEBLOCKS);
    }

}


class BlockEncoder extends Thread {
    int nrOfRepairSymbols = 0;
    int threadNumber;
    Iterator<SourceBlockEncoder> iterator;
    int blocksProcessed = 0;
    Lock iteratorLock;
    Semaphore availableDrops;
    ConcurrentLinkedQueue<byte[]> result;

    public void run() {
        while (true) {
            iteratorLock.lock();
            if (iterator.hasNext()) {
                SourceBlockEncoder blockToEncode = iterator.next();
                iteratorLock.unlock();
                encodeSourceBlock(blockToEncode);
                blocksProcessed++;
            } else {
                iteratorLock.unlock();
                System.out.println("Thread number " + threadNumber + " is done. It has processed " + blocksProcessed + " blocks");
                break;
            }
        }

    }

    public BlockEncoder(Iterator<SourceBlockEncoder> iterator, Lock iteratorLock, int threadNumber, Semaphore availableDrops, ConcurrentLinkedQueue<byte[]> result, int nrOfRepairSymbols) {
        this.threadNumber = threadNumber;
        this.iterator = iterator;
        this.iteratorLock = iteratorLock;
        this.availableDrops = availableDrops;
        this.result = result;
        this.nrOfRepairSymbols = nrOfRepairSymbols;
    }

    private void encodeSourceBlock(SourceBlockEncoder sbEnc) {

        // send all source symbols
        for (EncodingPacket pac : sbEnc.sourcePacketsIterable()) {
            sendPacket(pac);
        }

        // number of repair symbols
        int nr = numberOfRepairSymbols();

        // send nr repair symbols
        for (EncodingPacket pac : sbEnc.repairPacketsIterable(nr)) {
            sendPacket(pac);
        }
    }

    private int numberOfRepairSymbols() {
        return nrOfRepairSymbols;
    }

    private void sendPacket(EncodingPacket pac) {
        result.add(pac.asArray());
        availableDrops.release();
        // send the packet to the receiver
    }

}