package se.kth.chord.node;

import javax.xml.crypto.Data;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;

/**
 * Created by joakim on 2015-09-13.
 */
public class DataBlock implements Serializable{
    String name;
    Set<byte []> data;
    public DataBlock(String name){
        this.name = name;
    }
    public void addData(byte[] data){
        this.data.add(data);
    }
    public Set<byte[]> getData(){
        return data;
    }
    public String getName(){
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
