package se.kth.chord.node;

import javax.xml.crypto.Data;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by joakim on 2015-09-13.
 */
public class DataBlock implements Serializable, Comparable {
    String filename;
    int sequenceNumber;
    byte sourceBlockNumber;
    byte [] data;
    public DataBlock(String filename, int sequenceNumber, byte sourceBlockNumber){
        this.filename = filename;
        this.sequenceNumber = sequenceNumber;
        this.sourceBlockNumber = sourceBlockNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public byte getSourceBlockNumber() {
        return sourceBlockNumber;
    }

    public void setSourceBlockNumber(byte sourceBlockNumber) {
        this.sourceBlockNumber = sourceBlockNumber;
    }

    public void setData(byte [] data){
        this.data = data;
    }
    public  byte [] getData(){
        return data;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Override
    public int compareTo(Object o) {
        if (!(o instanceof DataBlock))
            throw new ClassCastException("Tried to compare to something that isn't a DataBlock");
        if(this.sourceBlockNumber>((DataBlock) o).sourceBlockNumber)
            return 1;
        else if(this.sourceBlockNumber<((DataBlock) o).sourceBlockNumber)
            return -1;
        else{
            if(this.sequenceNumber>((DataBlock) o).sequenceNumber){
                return 1;
            }
            else if(this.sequenceNumber<((DataBlock) o).sequenceNumber){
                return -1;
            }
        }
        return 0;
    }
}
