package se.kth.chord.msg.net;

import java.util.Set;

import se.kth.chord.msg.SendDataBlocks;
import se.kth.chord.node.DataBlock;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetSendDataBlocks extends NetMsg<SendDataBlocks> {
    public NetSendDataBlocks(NatedAddress src, NatedAddress dst, Set<DataBlock> blocks){super(src, dst, new SendDataBlocks(blocks));}

    public NetSendDataBlocks(Header<NatedAddress> header, SendDataBlocks content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetSendDataBlocks(newHeader, getContent());
    }
}
