package se.kth.chord.msg.net;

import se.kth.chord.msg.AddFile;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.HashMap;

/**
 * Created by joakim on 2015-09-13.
 */
public class NetAddFile extends NetMsg<AddFile> {
    public NetAddFile(NatedAddress src, NatedAddress dst, DataBlock dataBlock){super(src, dst, new AddFile(dataBlock));}

    public NetAddFile(Header<NatedAddress> header, AddFile content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetAddFile(newHeader, getContent());
    }
}
