package se.kth.chord.msg;

import java.io.Serializable;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.KompicsEvent;

public class RetrieveOriginalFile implements KompicsEvent, Serializable{
    String fileName;
    public RetrieveOriginalFile(String fileName){
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

}
