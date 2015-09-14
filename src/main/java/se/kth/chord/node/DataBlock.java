package se.kth.chord.node;

import javax.xml.crypto.Data;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by joakim on 2015-09-13.
 */
public class DataBlock implements Serializable{
    String filename;
    Set<Integer> sources;
    byte [] data;
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

    public Set<Integer> getSources() {
        return sources;
    }

    public void setSources(Set<Integer> sources) {
        this.sources = sources;
    }
}
