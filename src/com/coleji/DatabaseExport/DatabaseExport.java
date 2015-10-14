package com.coleji.DatabaseExport;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;

import com.coleji.Database.OracleConnectionManager;
import com.coleji.Database.QueryWrapper;


public class DatabaseExport {
	private static final int TABLE_NAME_COLUMN = 3;
	
	private static final char FIELD_DELIMITER = '\t';
	private static final char LINE_DELIMITER = '\n';

	private static final int COLUMN_TYPE_ORACLE_NUMBER = java.sql.Types.NUMERIC;
	private static final int COLUMN_TYPE_ORACLE_VARCHAR2 = java.sql.Types.VARCHAR;
	private static final int COLUMN_TYPE_ORACLE_CHAR = java.sql.Types.CHAR;
	private static final int COLUMN_TYPE_ORACLE_DATE = java.sql.Types.TIMESTAMP;
	private static final int COLUMN_TYPE_ORACLE_CLOB = java.sql.Types.CLOB;
	private static final int COLUMN_TYPE_ORACLE_BLOB = java.sql.Types.BLOB;
	
	private static final HashMap<Integer, Boolean> TYPES_FOR_MAIN_FILE;
	
	static {
		TYPES_FOR_MAIN_FILE = new HashMap<Integer, Boolean>();
		TYPES_FOR_MAIN_FILE.put(COLUMN_TYPE_ORACLE_NUMBER, true);
		TYPES_FOR_MAIN_FILE.put(COLUMN_TYPE_ORACLE_VARCHAR2, true);
		TYPES_FOR_MAIN_FILE.put(COLUMN_TYPE_ORACLE_CHAR, true);
		TYPES_FOR_MAIN_FILE.put(COLUMN_TYPE_ORACLE_DATE, true);
		TYPES_FOR_MAIN_FILE.put(COLUMN_TYPE_ORACLE_CLOB, false);
		TYPES_FOR_MAIN_FILE.put(COLUMN_TYPE_ORACLE_BLOB, false);
	}
	
	private static void exportLiveToFile(String directory, Connection c, String table) throws Exception {
		File f;
		BufferedWriter bw;
		QueryWrapper qw = new QueryWrapper();
		qw.add("select * from " + table);
		ResultSet rs = qw.runQueryAndGetResultSet(c);
		ResultSetMetaData rsmd = rs.getMetaData();
		int columnCount = rsmd.getColumnCount();
		HashMap<Integer,Boolean> writeIndexToMainFile = new HashMap<Integer,Boolean>();
		
		f = new File (directory + "/" + table + ".columns");
		if (!f.exists()) f.createNewFile();
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),"utf-8"));
		bw.write("EXPORTER_ID" + FIELD_DELIMITER);
		
		for (int i=0; i<columnCount; i++) {
			Integer columnType = rsmd.getColumnType(i+1);
			if (!TYPES_FOR_MAIN_FILE.containsKey(columnType)) {
				// TODO: column behavior not defined
				bw.close();
				throw new Exception();
			} 
			
			if (TYPES_FOR_MAIN_FILE.get(columnType)) {
				writeIndexToMainFile.put((i+1), true);
				bw.write(rsmd.getColumnName(i+1) + FIELD_DELIMITER);
			} else {
				writeIndexToMainFile.put((i+1), false);
			}
		}
		bw.close();
		
		f = new File (directory + "/" + table + ".data");
		if (!f.exists()) f.createNewFile();
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),"utf-8"));
		
		int rowCounter = 1;
		while (rs.next()) {
			bw.write("" + rowCounter++ + FIELD_DELIMITER);
			for (int i=0; i<columnCount; i++) {
				if (writeIndexToMainFile.get(i+1)) {
					bw.write(rs.getString(i+1) + FIELD_DELIMITER);
				} else {
					// write blob/clob data
				}
			}
			bw.write(LINE_DELIMITER);
		}
		bw.close();
	}
	
	public static void main(String[] args) {
		try {
			String writeToDirectory = "/home/jcole/export-test";
			Connection c = new OracleConnectionManager("/home/jcole/property-files/CBI_QA").getConnection();
			ResultSet tablesRS = c.getMetaData().getTables(null, "CBI_QA", null, new String[] {"TABLE"});
			ArrayList<String> tables = new ArrayList<String>();
			while (tablesRS.next()) {
				tables.add(tablesRS.getString(TABLE_NAME_COLUMN));
			}
			for (String table : tables) {
				if (!table.equals("EXPORT_TEST")) continue;
				
				exportLiveToFile(writeToDirectory, c, table);
			}
			c.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
