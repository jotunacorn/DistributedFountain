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

public class FountainCoder extends Thread{

    private ConcurrentLinkedQueue<byte[]> result;
    private Semaphore availableDrops;
    private Semaphore done;
    private Path pathToRead = Paths.get("C:\\Users\\joakim\\Downloads\\ubuntu-14.04.3-server-i386.iso");
    ;
    private int nrOfBytes = 512 * 1024 * 1024;


    //Encoding properties
    private static final int NR_OF_THREADS = 8;
    private static final int PAY_LEN = 4 * (1500 - 20 - 8); // UDP-Ipv4 payload times 4
    private static final int MAX_DEC_MEM = 4 * 1024 * 1024; // 4 MiB
    public static final long MAX_DATA_LEN = maxAllowedDataLength(PAY_LEN, MAX_DEC_MEM);    // The maximum allowed data length, given the parameters above

    //A simple usage sample for receiving droplets via a queue
    public static void main(String[] args) {
        FountainCoder fountainCoder = new FountainCoder(Paths.get("C:\\Users\\joakim\\Downloads\\ubuntu-14.04.3-server-i386.iso"), 256 * 1024 * 1024);
        Semaphore s = fountainCoder.dropsletsSemaphore();
        ConcurrentLinkedQueue<byte[]> result = fountainCoder.getQueue();
        Semaphore done = fountainCoder.getDoneLock();
        fountainCoder.start();
        int counter = 0;
        while (!done.tryAcquire() || result.peek()!=null) {
            boolean acquired = false;
            try {
                acquired = s.tryAcquire(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (acquired) {
                byte[] block = result.poll();
                counter++;
                System.out.println("Received block nr " + counter);
            }
        }
        System.out.println("All blocks (" + counter + ") received at main");
    }

    public FountainCoder(Path path, int nrOfBytes) {
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

    public void run() {
        System.out.println("The max length is " + MAX_DATA_LEN + " and the bytes to read is " + nrOfBytes);
        byte[] data = null;
        try {
            data = Files.readAllBytes(pathToRead);
        } catch (IOException e) {
            e.printStackTrace();
        }

        FECParameters parameters = getParameters(nrOfBytes);
        System.out.println("Created parameters with " + parameters.symbolSize() + " symbolsize, " + parameters.dataLengthAsInt() + " datalength " + " and " + parameters.numberOfSourceBlocks() + " sourceblocks.");
        DataEncoder encoder = OpenRQ.newEncoder(data, parameters);
        Iterator<SourceBlockEncoder> iter = encoder.sourceBlockIterable().iterator();
        System.out.println("Starting encoding process");
        long time = System.currentTimeMillis();
        BlockEncoder[] encoders = new BlockEncoder[NR_OF_THREADS];
        Lock l = new ReentrantLock();
        for (int i = 0; i < NR_OF_THREADS; i++) {
            encoders[i] = new BlockEncoder(iter, l, i, availableDrops, result);
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
        System.out.println("Finished with encoding " + nrOfBytes / 1000000 + "MB in " + finishTime + "ms. Average " + nrOfBytes / (finishTime * 1000) + " MB/s");
        System.out.println("The result list is currently " + result.size() + " long.");
//        System.out.println("Starting decoding");
//        time = System.currentTimeMillis();
//        ArrayDataDecoder decoder = OpenRQ.newDecoder(parameters, 1);
//        byte[] decodedFile = null;
//        int counter = 0;
//
//        while (!result.isEmpty()) {
//            EncodingPacket packetToDecode = result.poll();
//            Parsed<EncodingPacket> parsedPacket = decoder.parsePacket(packetToDecode.asArray(), false);
//            packetToDecode = parsedPacket.value();
//            if (parsedPacket.failureReason().length() < 2) {
//                counter++;
//            }
//            else{
//                System.out.println("Parser failure with reason " + parsedPacket.failureReason());
//            }
//            if (decoder.isDataDecoded()) {
//                System.out.println("Finished with encoding in " + (System.currentTimeMillis() - time) + "ms");
//                break;
//            }
//        }
//        decodedFile = decoder.dataArray();
//        System.out.println("Finished with decoding in " + (System.currentTimeMillis() - time) + "ms with " + counter + " iterations");
//        if (Arrays.equals(data, decodedFile)) {
//            System.out.println("The arrays match");
//        }
//        else{
//            System.out.println("It's a mismatch");
//            System.out.println("Size of msg is " + msg.length + " and size of decodedFile is " + decodedFile.length);
//            System.out.println("--------------MSG-------------");
//            System.out.println(new String(msg));
//            System.out.println("----------DECODEDFILE---------");
//            System.out.println(new String(decodedFile));
    }

    public static FECParameters getParameters(long dataLen) {

        if (dataLen < minDataLength())
            throw new IllegalArgumentException("data length is too small");
        if (dataLen > MAX_DATA_LEN)
            throw new IllegalArgumentException("data length is too large");

        return FECParameters.deriveParameters(dataLen, PAY_LEN, MAX_DEC_MEM);
    }

}


class BlockEncoder extends Thread {
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

    public BlockEncoder(Iterator<SourceBlockEncoder> iterator, Lock iteratorLock, int threadNumber, Semaphore availableDrops, ConcurrentLinkedQueue<byte[]> result) {
        this.threadNumber = threadNumber;
        this.iterator = iterator;
        this.iteratorLock = iteratorLock;
        this.availableDrops = availableDrops;
        this.result = result;
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
        return 1;
    }

    private void sendPacket(EncodingPacket pac) {
        result.add(pac.asArray());
        availableDrops.release();
        // send the packet to the receiver
    }

}