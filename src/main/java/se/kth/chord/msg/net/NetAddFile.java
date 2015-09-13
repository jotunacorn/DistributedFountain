package se.kth.chord.msg.net;

import se.kth.chord.msg.AddFile;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by joakim on 2015-09-13.
 */
public class NetAddFile extends NetMsg<AddFile> {
    public NetAddFile(NatedAddress src, NatedAddress dst, String fileName, byte [] file){super(src, dst, new AddFile(fileName, file));}

    public NetAddFile(Header<NatedAddress> header, AddFile content){super(header, content);}

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetAddFile(newHeader, getContent());
    }
}
