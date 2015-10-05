package se.kth.chord.msg;

import se.kth.chord.node.DataBlock;
import se.sics.kompics.KompicsEvent;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by joakim on 2015-09-13.
 */
public class AddFile implements KompicsEvent, Serializable{
    Set<DataBlock> dataBlocks;
    String filename;
    public AddFile(String filename, Set<DataBlock> dataBlocks){
        this.dataBlocks = dataBlocks;
        this.filename = filename;
    }

    public String getFilename() {
		return filename;
	}

	public Set<DataBlock> getDataBlocks() {
        return dataBlocks;
    }

}
