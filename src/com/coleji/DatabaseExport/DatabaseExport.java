package com.coleji.DatabaseExport;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.coleji.Database.OracleConnectionManager;
import com.coleji.Database.QueryWrapper;


public class DatabaseExport {
	private static final int TABLE_NAME_COLUMN = 3;
	
	protected static final char FIELD_DELIMITER = '\t';
	protected static final char LINE_DELIMITER = '\n';
	private static final char FILE_NAME_SEPARATOR = ' ';

	public static final int COLUMN_TYPE_ORACLE_NUMBER = java.sql.Types.NUMERIC;
	public static final int COLUMN_TYPE_ORACLE_VARCHAR2 = java.sql.Types.VARCHAR;
	public static final int COLUMN_TYPE_ORACLE_CHAR = java.sql.Types.CHAR;
	public static final int COLUMN_TYPE_ORACLE_DATE = java.sql.Types.TIMESTAMP;
	public static final int COLUMN_TYPE_ORACLE_CLOB = java.sql.Types.CLOB;
	public static final int COLUMN_TYPE_ORACLE_BLOB = java.sql.Types.BLOB;
	
	private static final Pattern DATA_FILE_REGEX = Pattern.compile("^(.+)\\.data$");
	private static final Pattern COLUMN_FILE_REGEX = Pattern.compile("^(.+)\\.columns$");
	private static final Pattern BLOB_CLOB_FILE_REGEX = Pattern.compile("^(.+) (.+) ([0-9]+)$");
	
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
	
	private DatabaseExport() {
		// can't be instantiated
	}
	
	private static void loadFilesToDatabase(String directory, Connection c) throws Exception {
		HashMap<String, TableConstructor> tablesHash = new HashMap<String, TableConstructor>();
		File directoryFile = new File(directory);
		if (!directoryFile.exists() || !directoryFile.isDirectory()) throw new Exception();
		
		for (File file : directoryFile.listFiles()) {
			String fileName = file.getName();
			Matcher m;
			if ((m = DATA_FILE_REGEX.matcher(fileName)).matches()) {
				String tableName = m.group(1);
				if (!tablesHash.containsKey(tableName)) tablesHash.put(tableName,new TableConstructor(tableName));
				tablesHash.get(tableName).putDataFile(file);
			} else if ((m = COLUMN_FILE_REGEX.matcher(fileName)).matches()) {
				String tableName = m.group(1);
				if (!tablesHash.containsKey(tableName)) tablesHash.put(tableName,new TableConstructor(tableName));
				tablesHash.get(tableName).putColumnsFile(file);
			} else if ((m = BLOB_CLOB_FILE_REGEX.matcher(fileName)).matches()) {
				String tableName = m.group(1);
				String columnName = m.group(2);
				Integer rowID = new Integer(m.group(3));
				if (!tablesHash.containsKey(tableName)) tablesHash.put(tableName,new TableConstructor(tableName));
				tablesHash.get(tableName).pushBlobFile(columnName, rowID, file);
			} else {
				// unrecognized file
				throw new Exception(fileName);
			}
		}
		Iterator<Entry<String, TableConstructor>> it = tablesHash.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, TableConstructor> e = it.next();
			System.out.println("validating " + e.getKey());
			e.getValue().validate();
		}
		
		it = tablesHash.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, TableConstructor> e = it.next();
			System.out.println("constructing " + e.getKey());
			e.getValue().construct(c);
		}
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
		if (f.exists()) {
			throw new Exception();
		}
		f.createNewFile();
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),"utf-8"));
	//	bw.write("EXPORTER_ID" + FIELD_DELIMITER);
		
		for (int i=0; i<columnCount; i++) {
			Integer columnType = rsmd.getColumnType(i+1);
			if (!TYPES_FOR_MAIN_FILE.containsKey(columnType)) {
				// TODO: column behavior not defined
				bw.close();
				throw new Exception();
			} 
			
			writeIndexToMainFile.put((i+1), TYPES_FOR_MAIN_FILE.get(columnType));
			bw.write(rsmd.getColumnName(i+1) + FIELD_DELIMITER + rsmd.getColumnType(i+1) + LINE_DELIMITER);
		}
		bw.close();
		System.out.println("\tDone with columns");
		
		f = new File (directory + "/" + table + ".data");
		if (f.exists()) {
			throw new Exception();
		}
		f.createNewFile();
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),"utf-8"));
		
		int rowCounter = 1;
		while (rs.next()) {
			System.out.println("\ttable : " +table + ", WRiting row " + rowCounter);
			bw.write("" + rowCounter + FIELD_DELIMITER);
			for (int i=0; i<columnCount; i++) {
				if (writeIndexToMainFile.get(i+1)) {
					bw.write(rs.getString(i+1) + FIELD_DELIMITER);
				} else if (rsmd.getColumnType(i+1) == COLUMN_TYPE_ORACLE_CLOB) {
					Clob clob = rs.getClob(i+1);
					if (clob == null) {
						continue;
					}
					File lobFile = new File (directory + "/" + table + FILE_NAME_SEPARATOR + rsmd.getColumnName(i+1) + FILE_NAME_SEPARATOR + rowCounter);
					if (lobFile.exists()) {
						bw.close();
						throw new Exception();
					}
					lobFile.createNewFile();
					FileOutputStream fos = new FileOutputStream(lobFile);
					System.out.println("" + clob.length());
					Reader r = clob.getCharacterStream();
					int b;
					while ((b = r.read()) != -1) {
						fos.write(b);
					}
					fos.close();
				} else if (rsmd.getColumnType(i+1) == COLUMN_TYPE_ORACLE_BLOB) {
					Blob blob = rs.getBlob(i + 1);
					if (blob == null) {
						continue;
					}
					File lobFile = new File (directory + "/" + table + FILE_NAME_SEPARATOR + rsmd.getColumnName(i+1) + FILE_NAME_SEPARATOR + rowCounter);
					if (lobFile.exists()) {
						bw.close();
						throw new Exception();
					}
					lobFile.createNewFile();
					FileOutputStream fos = new FileOutputStream(lobFile);
					InputStream is = blob.getBinaryStream();
					int b;
					while ((b = is.read()) != -1) {
						fos.write(b);
					}
					fos.close();
				} else {
					// should not get here
					bw.close();
					throw new Exception();
				}
			}
			bw.write(LINE_DELIMITER);
			rowCounter++;
		}
		bw.close();
	}
	
	public static void main(String[] args) {
	//	System.out.println(Pattern.compile("^(.)+ (.)+ [0-9]+$").matcher("d g 12").matches());
		
		try {
			String writeToDirectory = "/home/jcole/export-test";
			Connection c = new OracleConnectionManager("/home/jcole/property-files/CBI_QA").getConnection();
			
			ResultSet tablesRS = c.getMetaData().getTables(null, "CBI_QA", null, new String[] {"TABLE"});
			ArrayList<String> tables = new ArrayList<String>();
			while (tablesRS.next()) {
				tables.add(tablesRS.getString(TABLE_NAME_COLUMN));
			}
			for (String table : tables) {
				if (!table.equals("EXPORT_TEST_2")) continue;
				System.out.println("TABLE: " + table);
				exportLiveToFile(writeToDirectory, c, table);
			}

		//	loadFilesToDatabase(writeToDirectory, c);
			
			c.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
