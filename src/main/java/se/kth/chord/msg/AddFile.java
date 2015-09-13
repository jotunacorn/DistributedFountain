package se.kth.chord.msg;

import se.sics.kompics.KompicsEvent;

import java.io.Serializable;

/**
 * Created by joakim on 2015-09-13.
 */
public class AddFile implements KompicsEvent, Serializable{
    String name;
    byte [] file;
    public AddFile(String name, byte [] file){
        this.name = name;
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public byte[] getFile() {
        return file;
    }
}
