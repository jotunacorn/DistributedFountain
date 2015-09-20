package se.kth.chord.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Set;

import se.kth.chord.node.DataBlock;

/**
 * Created by joakim on 2015-09-14.
 */
public class TransferFile {
    String fileName;
    Set<DataBlock> fileData;
    NatedAddress sender;

    public TransferFile(String fileName, Set<DataBlock> fileData, NatedAddress sender) {
        this.fileName = fileName;
        this.sender = sender;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

	public Set<DataBlock> getFileData() {
		return fileData;
	}

	public void setFileData(Set<DataBlock> fileData) {
		this.fileData = fileData;
	}
}
