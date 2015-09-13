package se.kth.chord.component.init;

import se.kth.chord.component.NodeComp;
import se.sics.kompics.Init;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Set;

/**
 * Created by Mattias on 2015-04-21.
 */
public class NodeInit extends Init<NodeComp> {

    public final NatedAddress selfAddress;
    public final Set<NatedAddress> bootstrapNodes;
    public final NatedAddress aggregatorAddress;
    public final long seed;
    public NodeInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress, long seed) {
        this.selfAddress = selfAddress;
        this.bootstrapNodes = bootstrapNodes;
        this.aggregatorAddress = aggregatorAddress;
        this.seed = seed;
    }
}
