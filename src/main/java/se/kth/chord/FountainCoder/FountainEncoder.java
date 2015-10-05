package se.kth.chord.FountainCoder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
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
import se.kth.chord.node.DataBlock;


public class FountainEncoder extends Thread {


    private static final int BYTES_TO_READ = 128 * 1024 * 1024;
    private static final int NR_OF_RUNS = 102;
    private ConcurrentLinkedQueue<DataBlock> result;
    private Semaphore availableDrops;
    private Semaphore done;
    private Path pathToRead = Paths.get("C:\\Users\\joakim\\Downloads\\ubuntu-14.04.3-server-i386.iso");
    private static FECParameters parameters = null;

    private int nrOfBytes = 0;
    public static byte nextBlockNumber;
    //Encoding properties
    //Number of threads encoding
    public static final int NR_OF_THREADS = 8;
    //The number of sourceblocks needed
    private static final int NR_OF_SOURCEBLOCKS = 8; //If number of partitions is less than number of threads less threads will be used.
    // Fixed value for the symbol size
    private static final int SYMB_SIZE = 8 * (1500 - 20 - 8); // X * (UDP-Ipv4 payload length)
    // The maximum allowed data length, given the parameter above
    public static  long MAX_DATA_LEN = maxAllowedDataLength(SYMB_SIZE);
    // The redundancy in the system. This number should be higher than the anticipated loss. Chance of loss also increase with NR_OF_SOURCEBLOCKS
    public static final double ESTIMATED_LOSS = 0.325;

    //A simple usage sample for receiving droplets via a queue
    public static void main(String[] args) {
        long[][] results = new long[NR_OF_RUNS][2];
        boolean success[] = new boolean[NR_OF_RUNS];
        double chanceOfLoss = (double) 1 / (double) 3;
        for (int i = 0; i < NR_OF_RUNS; i++) {
            FountainEncoder fountainCoder = new FountainEncoder(Paths.get("C:\\Users\\joakim\\Downloads\\ubuntu-14.04.3-server-i386.iso"), BYTES_TO_READ); //New encoder with a Path to read
            Semaphore s = fountainCoder.dropsletsSemaphore();   //Semaphore to see if there are new droplets available
            ConcurrentLinkedQueue<DataBlock> result = fountainCoder.getQueue();    //Queue with the output
            long startTime = System.currentTimeMillis();
            fountainCoder.start();   //Start the encoder in a new thread
            int counter = 0;        //Count the number of droplets received
            long totalSize = 0;     //Count the total size of the output
            FountainDecoder decoder = new FountainDecoder(parameters);  //Create a new decoder with the same parameters as the encoder
            boolean firstAcquire = true;
            Semaphore done = fountainCoder.getDoneLock();
            LinkedList<DataBlock> acctualResult = new LinkedList<>();
            while (!done.tryAcquire() || result.peek() != null) {   //Run as long as we're getting blocks
                boolean acquired = false;   //See if there are new blocks available
                try {
                    acquired = s.tryAcquire(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (acquired) { //We've gotten a new block

                    DataBlock block = result.poll();   //retrive the block
                    totalSize = totalSize + block.getData().length;
                    //if (Math.random() > (chanceOfLoss)) {        //Throw away some files
                        acctualResult.add(block);      //Add the block to the decoder
                    //}
                    counter++;
                    //if (counter % 5000 == 0)  System.out.println("Received block nr " + counter);
                }

            }
            results[i][0] = System.currentTimeMillis() - startTime;
            //Shuffle the packets
            //Collections.shuffle(acctualResult);

            acctualResult.forEach(decoder::addDataBlock);

            decoder.setParameters(parameters);
            startTime = System.nanoTime();
            decoder.start();
           // System.out.println(i + ">All blocks (" + counter + ") received at main. The encoded files are " + totalSize / 1000000 + "MB. It took " + results[i][0] + " ms");
            long time = System.currentTimeMillis();
            try {
                decoder.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            results[i][1] = System.nanoTime() - startTime;
            //System.out.println("Done with decoding. The decoding was done in " + results[i][1] / 1000000 + "ms.");
            success[i] = decoder.success();
        }
        //System.out.println("Encoding time");
        for (int i = 0; i < NR_OF_RUNS; i++) {
           // System.out.println(results[i][0]);
        }
        System.out.println("Decoding time");
        for (int i = 0; i < NR_OF_RUNS; i++) {
            //System.out.println(results[i][1] / 1000000);
        }
        System.out.println("Successes");
        for (int i = 0; i < NR_OF_RUNS; i++) {
            if (success[i]) {
               // System.out.println(1);
            } else {
               // System.out.println(0);
            }
        }
    }

    public FountainEncoder(Path path, int nrOfBytes) {
        this.nrOfBytes = nrOfBytes;
        this.pathToRead = path;
        result = new ConcurrentLinkedQueue<>();
        availableDrops = new Semaphore(0, false);
        done = new Semaphore(0, false);
    }

    public ConcurrentLinkedQueue<DataBlock> getQueue() {
        return result;
    }

    public Semaphore dropsletsSemaphore() {
        return availableDrops;
    }

    public Semaphore getDoneLock() {
        return this.done;
    }

    public FECParameters getParameters() {
        return parameters;
    }

    public void run() {
        //System.out.println("The max length is " + MAX_DATA_LEN + " and the bytes to read is " + nrOfBytes);
        FountainEncoder.nextBlockNumber = 0;
        byte[] data = null;
        try {
            data = Files.readAllBytes(pathToRead);
        } catch (IOException e) {
            e.printStackTrace();
        }
        parameters = getParameters(nrOfBytes);
//        System.out.println("Created parameters with \n " +
//                "Symbolsize:" + parameters.symbolSize() +
//                "\n Datalength:" + parameters.dataLengthAsInt() +
//                "\n NumberOfSourceBlocks:" + parameters.numberOfSourceBlocks() +
//                "\n TotalSymbols:" + parameters.totalSymbols());
        int nrOfRepairSymbols = OpenRQ.minRepairSymbols(parameters.totalSymbols(), 0, ESTIMATED_LOSS);
        //System.out.println("The number of symbols needed to be transmitted are " + nrOfRepairSymbols + "(" + String.format("%.2f", ((double) nrOfRepairSymbols / (double) parameters.totalSymbols()) * 100) + "%)");
        nrOfRepairSymbols = (nrOfRepairSymbols / NR_OF_SOURCEBLOCKS) + 1;
        DataEncoder encoder = OpenRQ.newEncoder(data, parameters);
        Iterator<SourceBlockEncoder> iter = encoder.sourceBlockIterable().iterator();
        //System.out.println("Starting encoding process");
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
        //System.out.println("Finished with encoding " + nrOfBytes / (1024 * 1024) + "MiB in " + finishTime + "ms. Average " + nrOfBytes / (finishTime * 1024) + " MB/s");
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
    byte currentBlockNumber = 0;
    int currentPacket = 0;
    int threadNumber;
    Iterator<SourceBlockEncoder> iterator;
    int blocksProcessed = 0;
    Lock iteratorLock;
    Semaphore availableDrops;
    ConcurrentLinkedQueue<DataBlock> result;

    public void run() {
        while (true) {
            iteratorLock.lock();
            if (iterator.hasNext()) {
                currentBlockNumber = FountainEncoder.nextBlockNumber;
                FountainEncoder.nextBlockNumber++;
                SourceBlockEncoder blockToEncode = iterator.next();
                iteratorLock.unlock();
                currentPacket = 0;
                encodeSourceBlock(blockToEncode);
                blocksProcessed++;
            } else {
                iteratorLock.unlock();
                //System.out.println("Thread number " + threadNumber + " is done. It has processed " + blocksProcessed + " blocks");
                break;
            }
        }

    }

    public BlockEncoder(Iterator<SourceBlockEncoder> iterator, Lock iteratorLock, int threadNumber, Semaphore availableDrops, ConcurrentLinkedQueue<DataBlock> result, int nrOfRepairSymbols) {
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
            currentPacket++;
        }

        // number of repair symbols
        int nr = numberOfRepairSymbols();

        // send nr repair symbols
        for (EncodingPacket pac : sbEnc.repairPacketsIterable(nr)) {
            currentPacket++;
            sendPacket(pac);
        }
    }

    private int numberOfRepairSymbols() {
        return nrOfRepairSymbols;
    }

    private void sendPacket(EncodingPacket pac) {
        DataBlock newBlock = new DataBlock("Test", currentPacket, currentBlockNumber);
        newBlock.setData(pac.asArray());
        result.add(newBlock);
        availableDrops.release();
        // send the packet to the receiver
    }

    private double[] dennisAverage() {
        int base = 2;
        int exp = 1;
        int k = 1;
        int numbers = 10;
        double [] weights = new double[numbers];

        for( int i = 0; i<weights.length; i++){
            weights[i] = k*i*Math.pow(base,exp*i);
        }
        return weights;
    }
}