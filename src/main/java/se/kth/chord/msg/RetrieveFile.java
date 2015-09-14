package se.kth.chord.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * Created by joakim on 2015-09-14.
 */
public class RetrieveFile {
    String fileName;
    NatedAddress requester;

    public RetrieveFile(String fileName, NatedAddress requester) {
        this.fileName = fileName;
        this.requester = requester;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
