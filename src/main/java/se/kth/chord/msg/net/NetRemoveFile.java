package se.kth.chord.msg.net;

import se.kth.chord.msg.RemoveFile;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetRemoveFile extends NetMsg<RemoveFile> {
    public NetRemoveFile(NatedAddress src, NatedAddress dst, String fileName){super(src, dst, new RemoveFile(fileName));}

    public NetRemoveFile(Header<NatedAddress> header, RemoveFile content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetRemoveFile(newHeader, getContent());
    }
}
