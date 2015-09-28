package se.kth.chord.msg.net;

import se.kth.chord.msg.AddFile;
import se.kth.chord.msg.AddOriginalFile;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetAddOriginalFile extends NetMsg<AddOriginalFile> {
    public NetAddOriginalFile(NatedAddress src, NatedAddress dst, String fileName){super(src, dst, new AddOriginalFile(fileName));}

    public NetAddOriginalFile(Header<NatedAddress> header, AddOriginalFile content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetAddOriginalFile(newHeader, getContent());
    }
}
