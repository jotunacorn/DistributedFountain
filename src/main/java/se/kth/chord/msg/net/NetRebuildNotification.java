package se.kth.chord.msg.net;

import se.kth.chord.msg.RebuildNotification;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetRebuildNotification extends NetMsg<RebuildNotification> {
    public NetRebuildNotification(NatedAddress src, NatedAddress dst, String fileName){super(src, dst, new RebuildNotification(fileName));}

    public NetRebuildNotification(Header<NatedAddress> header, RebuildNotification content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetRebuildNotification(newHeader, getContent());
    }
}
