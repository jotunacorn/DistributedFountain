package se.kth.chord.msg;

import se.kth.chord.node.DataBlock;
import se.sics.kompics.KompicsEvent;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by joakim on 2015-09-13.
 */
public class AddFile implements KompicsEvent, Serializable{
    DataBlock dataBlock;
    public AddFile(DataBlock dataBlock){
        this.dataBlock = dataBlock;
    }

    public DataBlock getDataBlock() {
        return dataBlock;
    }

}
