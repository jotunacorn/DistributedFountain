package se.kth.chord.FountainCoder;

import sun.awt.image.ImageWatched;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class Main {


	private static final int NR_OF_BLOCKS = 4000;
	private static final int BYTES_TO_READ = 2048000;
	public static void main(String[] args) {

		Path path = Paths.get("C:\\Users\\joakim\\Downloads\\node-v0.12.7-x86.msi");
		byte[] data= new byte[BYTES_TO_READ];
		try {
			InputStream reader = Files.newInputStream(path);
            reader.read(data);
		} catch (IOException e) {
			e.printStackTrace();
		}


		byte[] msg = new byte[BYTES_TO_READ];
		for(int i = 0; i < BYTES_TO_READ; i++){
			msg[i] = data[i];
		}
        //System.out.println(new String(msg));
		int blockSize = (msg.length/NR_OF_BLOCKS);

		System.out.println("Starting encoding and decoding. Encoding " + msg.length/blockSize + " blocks at " + blockSize + " bytes per block.");
		long time = System.nanoTime();
		Encoder enc = new Encoder(msg.length/blockSize, blockSize);

		EncodingThread eThread = new EncodingThread(enc, msg);
		DecodingThread dThread = new DecodingThread(enc, msg);
		
		eThread.start();
		dThread.start();
		
		try {
			eThread.join();
			dThread.join();
		} catch (InterruptedException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.exit(-3);
		}
		System.out.println("Finished in " + (System.nanoTime()-time)/1000000 + "ms");
	}
}

class EncodingThread extends Thread {
    
	Encoder enc;
	byte[] msg;
	List<Block> encMsg;
	
    EncodingThread(Encoder e, byte[] m) {
        enc = e;
        msg = m;
    }

    public void run() {
    	
        encMsg = enc.encode(new String(msg));
                
        //System.out.println("------ ENCODED MESSAGE ------");
        /*
        int i=1;
        for(Block b : encMsg){
        	String s = new String(b.getData());
        	
        	if(!(s.equals(""))){
        		for(int j=0; j<32; j++)
        			System.out.print(b.getData()[j]);
    			if(i%4==0) System.out.println("");
        	}
        	i++;
        }
        System.out.println("");
        */
    }
}

class DecodingThread extends Thread {
    
	Encoder enc;
	byte [] input;
    DecodingThread(Encoder e, byte [] input) {
        enc = e;
        this.input = input;
    }

    public void run() {
    	byte [] result = enc.decode();
        System.out.println(new String(result));
		System.out.println("The length of result is " + result.length + " and the length of input is " + input.length);

        Collection<Byte> listOne = new ArrayList<>();
        Collection<Byte> listTwo = new ArrayList<>();
        int diffNumber = 0;
        for(int i = 0; i<result.length; i++){
            if(result[i] != input[i]){
                diffNumber++;
                System.out.println("Found dif nr "+ diffNumber + " at index " + i + ". Result is " + result[i] + " and input is " + input[i]);
            }
            listOne.add(result[i]);
            listTwo.add(input[i]);
        }

        System.out.println("");
        if(Arrays.equals(input, result)){
            System.out.println("The arrays are the same!");
        }
        else{
            System.out.println("The arrays differ");
        }
    }
    public static LinkedList<Byte> differences(byte[] first, byte[] second) {
        byte[] sortedFirst = Arrays.copyOf(first, first.length); // O(n)
        byte[] sortedSecond = Arrays.copyOf(second, second.length); // O(m)
        Arrays.sort(sortedFirst); // O(n log n)
        Arrays.sort(sortedSecond); // O(m log m)

        int firstIndex = 0;
        int secondIndex = 0;

        LinkedList<Byte> diffs = new LinkedList<Byte>();
        boolean firstDiff = true;
        while (firstIndex < sortedFirst.length && secondIndex < sortedSecond.length) { // O(n + m)
            int compare = 0;

            if(sortedFirst[firstIndex]>sortedSecond[secondIndex]){
                if(firstDiff) {
                    System.out.println("Found diff at indexes " + firstIndex + ":" + secondIndex);
                    firstDiff = false;
                }
                compare = 1;
            }
            else if(sortedFirst[firstIndex]<sortedSecond[secondIndex]){
                if(firstDiff) {
                    System.out.println("Found diff at indexes " + firstIndex + ":" + secondIndex);
                    firstDiff = false;
                }
                compare = 1;
            }
            switch(compare) {
                case -1:
                    diffs.add(sortedFirst[firstIndex]);
                    firstIndex++;
                    break;
                case 1:
                    diffs.add(sortedSecond[secondIndex]);
                    secondIndex++;
                    break;
                default:
                    firstIndex++;
                    secondIndex++;
            }
        }
        return diffs;
    }
}
