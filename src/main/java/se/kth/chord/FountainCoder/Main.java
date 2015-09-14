package se.kth.chord.FountainCoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


public class Main {


	private static final int NR_OF_BLOCKS = 1000;
	private static final int BYTES_TO_READ = 128000;
	public static void main(String[] args) {

		Path path = Paths.get("C:\\Users\\joakim\\Downloads\\sv_windows_10_education_x64_dvd_6848214.iso");
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
		System.out.println(new String(msg));
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
        System.out.println("------ DECODED MESSAGE ------\n" + new String(result));
        if(Arrays.equals(input, result)){
            System.out.println("The arrays are the same!");
        }
        else{
            System.out.println("The arrays differ");
        }
    }
}