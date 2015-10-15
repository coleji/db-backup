package com.coleji.DatabaseExport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.HashMap;

public class TableConstructor {
	private File dataFile, columnsFile;
	private HashMap<String,HashMap<Integer,File>> blobFiles;
	private String[] columnNames;
	
	protected TableConstructor() {
		this.dataFile = null;
		this.columnsFile = null;
		this.blobFiles = new HashMap<String,HashMap<Integer,File>>();
		this.columnNames = null;
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
	
	protected void validate() throws Exception {
		if (this.dataFile == null || this.columnsFile == null) {
			throw new Exception();
		}
		
		BufferedReader br = new BufferedReader(new FileReader(this.columnsFile));
		String line = br.readLine();
		if (br.readLine() != null) {
			// column file should only be one line
			br.close();
			throw new Exception();
		}
		br.close();
		this.columnNames = line.split((new Character(DatabaseExport.FIELD_DELIMITER)).toString());
		int columnCount = this.columnNames.length;
		
		br = new BufferedReader(new FileReader(this.dataFile));
		while ((line = br.readLine()) != null) {
			String[] values = line.split((new Character(DatabaseExport.FIELD_DELIMITER)).toString());
		}
	}
}
