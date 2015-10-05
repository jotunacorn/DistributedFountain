package se.kth.chord.msg.net;

import se.kth.chord.msg.RemoveOriginalFile;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetRemoveOriginalFile extends NetMsg<RemoveOriginalFile> {
    public NetRemoveOriginalFile(NatedAddress src, NatedAddress dst, String fileName){super(src, dst, new RemoveOriginalFile(fileName));}

    public NetRemoveOriginalFile(Header<NatedAddress> header, RemoveOriginalFile content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetRemoveOriginalFile(newHeader, getContent());
    }
}
