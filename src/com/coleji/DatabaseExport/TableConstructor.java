package com.coleji.DatabaseExport;

import java.io.File;
import java.util.HashMap;

public class TableConstructor {
	private File dataFile, columnsFile;
	private HashMap<String,HashMap<Integer,File>> blobFiles;
	
	protected TableConstructor() {
		this.dataFile = null;
		this.columnsFile = null;
		this.blobFiles = new HashMap<String,HashMap<Integer,File>>();
	}
	
	protected void putDataFile(File f) throws Exception {
		if (this.dataFile == null) {
			this.dataFile = f;
		} else {
			throw new Exception();
		}
	}
	
	protected void putColumnsFile(File f) throws Exception {
		if (this.columnsFile == null) {
			this.columnsFile = f;
		} else {
			throw new Exception();
		}
	}
	
	protected void pushBlobFile(String columnName, Integer rowID, File f) throws Exception {
		if (!this.blobFiles.containsKey(columnName)) this.blobFiles.put(columnName, new HashMap<Integer,File>());
		if (!this.blobFiles.get(columnName).containsKey(rowID)) {
			this.blobFiles.get(columnName).put(rowID,f);
		} else {
			throw new Exception();
		}
	}
}
