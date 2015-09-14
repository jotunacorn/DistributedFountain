package se.kth.chord.msg.net;

import se.kth.chord.msg.RetrieveFile;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by joakim on 2015-09-14.
 */
public class NetRetrieveFile extends NetMsg<RetrieveFile>{

    public NetRetrieveFile(NatedAddress src, NatedAddress dst, RetrieveFile content) {
        super(src, dst, content);
    }

    public NetRetrieveFile(Header<NatedAddress> header, RetrieveFile content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return null;
    }
}
