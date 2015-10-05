package se.kth.chord.msg.net;

import se.kth.chord.msg.RetrieveOriginalFile;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetRetrieveOriginalFile extends NetMsg<RetrieveOriginalFile> {
    public NetRetrieveOriginalFile(NatedAddress src, NatedAddress dst, String fileName){super(src, dst, new RetrieveOriginalFile(fileName));}

    public NetRetrieveOriginalFile(Header<NatedAddress> header, RetrieveOriginalFile content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetRetrieveOriginalFile(newHeader, getContent());
    }
}
