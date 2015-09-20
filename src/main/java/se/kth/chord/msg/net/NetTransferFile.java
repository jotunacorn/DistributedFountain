package se.kth.chord.msg.net;

import se.kth.chord.msg.TransferFile;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by joakim on 2015-09-14.
 */
public class NetTransferFile extends NetMsg<TransferFile>{

    public NetTransferFile(NatedAddress src, NatedAddress dst, TransferFile content) {
        super(src, dst, content);
    }

    public NetTransferFile(Header<NatedAddress> header, TransferFile content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return null;
    }
}
