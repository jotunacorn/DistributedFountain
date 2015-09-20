package se.kth.chord.FountainCoder;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.fec.openrq.*;
import net.fec.openrq.encoder.DataEncoder;
import net.fec.openrq.encoder.SourceBlockEncoder;

import static net.fec.openrq.parameters.ParameterChecker.maxAllowedDataLength;
import static net.fec.openrq.parameters.ParameterChecker.minDataLength;

import net.fec.openrq.parameters.FECParameters;

public class FountainCoder {

    public static ConcurrentLinkedQueue<EncodingPacket> result;
    private static final int BYTES_TO_READ = 512 * 1024 * 1024;
    private static final int NR_OF_THREADS = 8;
    private static final int PAY_LEN = 1500 - 20 - 8; // UDP-Ipv4 payload length
    public static int numberOfEncodedBlocks = 1;
    public static int nrOfBlocksToEncode = 0;
    // Fixed value for the maximum decoding block size
    private static final int MAX_DEC_MEM = 4 * 1024 * 1024; // 4 MiB

    // The maximum allowed data length, given the parameters above
    public static final long MAX_DATA_LEN = maxAllowedDataLength(PAY_LEN, MAX_DEC_MEM);


    public static void main(String[] args) {
        System.out.println("The max length is " + MAX_DATA_LEN + " and the bytes to read is " + BYTES_TO_READ);
        result = new ConcurrentLinkedQueue<>();
        Path path = Paths.get("C:\\Users\\joakim\\Downloads\\ubuntu-14.04.3-server-i386.iso");
        byte[] data = null;
        try {
            data = Files.readAllBytes(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        FECParameters parameters = getParameters(BYTES_TO_READ);
        System.out.println("Created parameters with " +  parameters.symbolSize() + " symbolsize, " + parameters.dataLengthAsInt() + " datalength " + " and " + parameters.numberOfSourceBlocks() + " number of sourceblocks.");
        nrOfBlocksToEncode = parameters.numberOfSourceBlocks();
        DataEncoder encoder = OpenRQ.newEncoder(data, parameters);
        Iterator<SourceBlockEncoder> iter = encoder.sourceBlockIterable().iterator();
        System.out.println("Starting encoding process");
        long time = System.currentTimeMillis();
        BlockEncoder[] encoders = new BlockEncoder[NR_OF_THREADS];
        Lock l = new ReentrantLock();
        for (int i = 0; i < NR_OF_THREADS; i++) {
            encoders[i] = new BlockEncoder(iter, l, i);
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
        System.out.println("Finished with encoding in " + (System.currentTimeMillis() - time) + "ms");
        System.out.println("The result list is " + result.size() + " long.");
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
    int blocksProccessed = 0;
    Lock iteratorLock;

    public void run() {
        while (true) {
            iteratorLock.lock();
            if (iterator.hasNext()) {
                SourceBlockEncoder blockToEncode = iterator.next();
                iteratorLock.unlock();
                encodeSourceBlock(blockToEncode);
                blocksProccessed++;
                System.out.println(threadNumber + "> Encoding at " + String.format("%.1f",round(((double)FountainCoder.numberOfEncodedBlocks++/(double)FountainCoder.nrOfBlocksToEncode),3)*100) + "%");
            } else {
                iteratorLock.unlock();
                System.out.println("Thread number " + threadNumber + " is done. It has processed " + blocksProccessed + " blocks");
                break;
            }
        }

    }

    public BlockEncoder(Iterator<SourceBlockEncoder> iterator, Lock iteratorLock, int threadNumber) {
        this.threadNumber = threadNumber;
        this.iterator = iterator;
        this.iteratorLock = iteratorLock;
    }

    private static void encodeSourceBlock(SourceBlockEncoder sbEnc) {

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

    private static int numberOfRepairSymbols() {
        return 1;
    }

    private static void sendPacket(EncodingPacket pac) {
        FountainCoder.result.add(pac);
        // send the packet to the receiver
    }
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

}