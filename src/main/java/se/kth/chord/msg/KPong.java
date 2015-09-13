package se.kth.chord.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by Mattias on 2015-04-24.
 */
public class KPong {

    private NatedAddress address;
    private int incarnationCounter;
    private int pingNr;

    public KPong(NatedAddress address, int incarnationCounter, int pingNr) {
        this.address = address;
        this.incarnationCounter = incarnationCounter;
        this.pingNr = pingNr;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }

    public void setIncarnationCounter(int incarnationCounter) {
        this.incarnationCounter = incarnationCounter;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }

}
