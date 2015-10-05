package se.kth.chord.msg.net;

import se.kth.chord.msg.AddFile;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by joakim on 2015-09-13.
 */
public class NetAddFile extends NetMsg<AddFile> {
    public NetAddFile(NatedAddress src, NatedAddress dst, String filename, Set<DataBlock> dataBlocks){super(src, dst, new AddFile(filename, dataBlocks));}

    public NetAddFile(Header<NatedAddress> header, AddFile content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetAddFile(newHeader, getContent());
    }
}
