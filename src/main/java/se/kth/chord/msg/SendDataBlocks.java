package se.kth.chord.msg;

import java.io.Serializable;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.KompicsEvent;
import java.util.Set;

public class SendDataBlocks implements KompicsEvent, Serializable{
    Set<DataBlock> blocks;
    public SendDataBlocks(Set<DataBlock> blocks){
        this.blocks = blocks;
    }

    public Set<DataBlock> getDataBlocks() {
        return blocks;
    }

}
