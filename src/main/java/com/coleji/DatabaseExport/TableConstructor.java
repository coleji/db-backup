package com.coleji.DatabaseExport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import com.coleji.Database.QueryWrapper;

public class TableConstructor {
	private String tableName;
	private File dataFile, columnsFile;
	private HashMap<Integer,HashMap<String,File>> blobFiles;
	//private String[] columnNames;
	private ArrayList<Column> columns;
	
	// If there are e.g. 3 non-blob columns, then 2 blob columns, then 2 more nonblob columns, the datafile will have 5 positions whereas this.columns will have 7 columns
	// So this.dataFileColIndexToObjectColumnIndex holds the mapping of file column position to this.columns potition like so:
	// 0 -> 0
	// 1 -> 1
	// 2 -> 2
	//     ( two blob columns here)
	// 3 -> 5
	// 4 -> 6
	private HashMap<Integer,Integer> dataFileColIndexToObjectColumnIndex;
	
	protected TableConstructor(String tableName) {
		this.tableName = tableName;
		this.dataFile = null;
		this.columnsFile = null;
		this.blobFiles = new HashMap<Integer,HashMap<String,File>>();
		this.columns = null;
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
		if (!this.blobFiles.containsKey(rowID)) this.blobFiles.put(rowID, new HashMap<String,File>());
		if (!this.blobFiles.get(rowID).containsKey(columnName)) {
			this.blobFiles.get(rowID).put(columnName,f);
		} else {
			throw new Exception();
		}
	}
	
	protected void validate() throws Exception {
		// pointers to both files?
		if (this.dataFile == null || this.columnsFile == null) {
			throw new Exception();
		}
		
		// Define all column objects
		BufferedReader br = new BufferedReader(new FileReader(this.columnsFile));
		String line;
		this.columns = new ArrayList<Column>();
		while ((line = br.readLine()) != null) {
			String[] components = line.split((new Character(DatabaseExport.FIELD_DELIMITER)).toString());
			this.columns.add(new Column(components[0], new Integer(components[1])));
		}
		br.close();
		
		this.dataFileColIndexToObjectColumnIndex = new HashMap<Integer, Integer>();
		int columnCountInMainFile = 1; // start with 1 for that exporterID
		for (int i=0; i<this.columns.size(); i++) {
			switch(this.columns.get(i).columnType) {
			case DatabaseExport.COLUMN_TYPE_ORACLE_VARCHAR2:
			case DatabaseExport.COLUMN_TYPE_ORACLE_CHAR:
			case DatabaseExport.COLUMN_TYPE_ORACLE_DATE:
			case DatabaseExport.COLUMN_TYPE_ORACLE_NUMBER:
				dataFileColIndexToObjectColumnIndex.put(columnCountInMainFile, i);
				columnCountInMainFile++;
				break;
			case DatabaseExport.COLUMN_TYPE_ORACLE_CLOB:
			case DatabaseExport.COLUMN_TYPE_ORACLE_BLOB:
				break;
			default:
				throw new Exception();
			}
		}
		
		// Read data file, make sure it has the correct # columns on each row.
		// TODO: make sure there are no line endings in the data.  Escape newlines on file creation?
		br = new BufferedReader(new FileReader(this.dataFile));
		while ((line = br.readLine()) != null) {
			line = line + "$";
			// If the line ends in a bunch of empty fields, they will get trunc'd off when we split
			// To prevent that from happening, 
			String[] values = line.split((new Character(DatabaseExport.FIELD_DELIMITER)).toString());
			if (values.length - 1 != columnCountInMainFile) {
				br.close();
	//			System.out.println("\"" + line + "\"");
				throw new Exception("Found a row with " + (values.length - 1) + " columns when it should have had " + columnCountInMainFile);
			} else {
	//			System.out.println(values[values.length-1]);
			}
		}
		// TODO: take connection and validate that the columns are right?
	}
	
	// assumes validate() has run to populate the column definition stuff
	protected void construct(Connection c) throws Exception {
		QueryWrapper qw;
		String line;
		BufferedReader br = new BufferedReader(new FileReader(this.dataFile));
		
		
		// Read each data row, create an insert statement for non-blob fields, then one insert per blob
		while ((line = br.readLine()) != null) {
			line = line + "$";
			String delimiter = "";
			String[] values = line.split((new Character(DatabaseExport.FIELD_DELIMITER)).toString());
			qw = new QueryWrapper();
			qw.add("INSERT INTO " + this.tableName + " (");
			for (Column column : this.columns) {
				switch(column.columnType) {
				case DatabaseExport.COLUMN_TYPE_ORACLE_VARCHAR2:
				case DatabaseExport.COLUMN_TYPE_ORACLE_CHAR:
				case DatabaseExport.COLUMN_TYPE_ORACLE_DATE:
				case DatabaseExport.COLUMN_TYPE_ORACLE_NUMBER:
					qw.add(delimiter + column.columnName);
					break;
				case DatabaseExport.COLUMN_TYPE_ORACLE_CLOB:
				case DatabaseExport.COLUMN_TYPE_ORACLE_BLOB:
					break;
				default:
					throw new Exception();
				}
				
				delimiter = ", ";
			}
			delimiter = "";
			qw.add(") VALUES (");
			// FIXME: switch on column type, add quotes for strings, to_date() for dates etc
			for (int i=1; i<values.length-1; i++) {
				String value = values[i];
				if (value == null || value.equals("")) {
					qw.add(delimiter + "null");
				} else {/*
					System.out.println("\"" + value + "\"");
					char[] cs = value.toCharArray();
					for (char c1 : cs) {
						System.out.print((int)c1 + ":");
					}*/
					Column col = this.columns.get(dataFileColIndexToObjectColumnIndex.get(i));
					switch(col.columnType) {
					case DatabaseExport.COLUMN_TYPE_ORACLE_VARCHAR2:
					case DatabaseExport.COLUMN_TYPE_ORACLE_CHAR:
						for (int j=DatabaseExport.ESCAPES.length; j>0; j--) {
							CharSequence[] escape = DatabaseExport.ESCAPES[j-1];
							value.replace(escape[1], escape[0]);
						}
						value = value.replace("'", "''");
						qw.add(delimiter + "'" + value + "'");
						break;
					case DatabaseExport.COLUMN_TYPE_ORACLE_DATE:
					//	System.out.println("\"" + value + "\"");
						qw.add(delimiter + "to_date('" + value.substring(0,value.length() - 2) + "','YYYY-MM-DD HH24:MI:SS')");
						break;
					case DatabaseExport.COLUMN_TYPE_ORACLE_NUMBER:
						qw.add(delimiter + value);
						break;
					case DatabaseExport.COLUMN_TYPE_ORACLE_CLOB:
					case DatabaseExport.COLUMN_TYPE_ORACLE_BLOB:
						break;
					default:
						throw new Exception();
					}
				}
				delimiter = ", ";
			}
			qw.add(")");
	//		System.out.println(qw.toString());
			qw.runUpdateOrDelete(c);
			
			Integer rowID = new Integer(values[0]);
			if (this.blobFiles.containsKey(rowID)) {
				Iterator<Entry<String, File>> it = this.blobFiles.get(rowID).entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, File> e = it.next();
					Integer columnType = null;
					for (Column column : this.columns) {
						if (column.columnName.equals(e.getKey())) {
							columnType = column.columnType;
							break;
						}
					}
					StringBuilder s = new StringBuilder();
					s.append("UPDATE " + this.tableName + " SET " + e.getKey() + " = ? ");
					delimiter = " WHERE ";
					for (int i=1; i<values.length-1; i++) {
						String value = values[i];
						Column col = this.columns.get(dataFileColIndexToObjectColumnIndex.get(i));
						if (value == null || value.equals("")) {
							s.append(delimiter + col.columnName + " is null");
						} else {
							switch(col.columnType) {
							case DatabaseExport.COLUMN_TYPE_ORACLE_VARCHAR2:
							case DatabaseExport.COLUMN_TYPE_ORACLE_CHAR:
								for (int j=DatabaseExport.ESCAPES.length; j>0; j--) {
									CharSequence[] escape = DatabaseExport.ESCAPES[j-1];
									value.replace(escape[1], escape[0]);
								}
								value = value.replace("'", "''");
								s.append(delimiter + col.columnName + " = '" + value + "'");
								break;
							case DatabaseExport.COLUMN_TYPE_ORACLE_DATE:
								s.append(delimiter + col.columnName + " = to_date('" + value.substring(0,value.length() - 2) + "','YYYY-MM-DD HH24:MI:SS')");
								break;
							case DatabaseExport.COLUMN_TYPE_ORACLE_NUMBER:
								s.append(delimiter + col.columnName + " = " + value);
								break;
							case DatabaseExport.COLUMN_TYPE_ORACLE_CLOB:
							case DatabaseExport.COLUMN_TYPE_ORACLE_BLOB:
								break;
							default:
								throw new Exception();
							}
						}
						delimiter = " AND ";
					}
		//			System.out.println(s.toString());
					PreparedStatement ps = c.prepareStatement(s.toString());
					if (columnType == DatabaseExport.COLUMN_TYPE_ORACLE_BLOB) {
						FileInputStream fis = new FileInputStream(e.getValue());
						ps.setBinaryStream(1, fis, (int)e.getValue().length());
						int rowsUpdated = ps.executeUpdate();
						System.out.println(rowsUpdated + "  " + s.toString());
						fis.close();
					} else if (columnType == DatabaseExport.COLUMN_TYPE_ORACLE_CLOB) {
					//	FileReader fr = new FileReader(e.getValue(),"utf-8");
						InputStreamReader ir = new InputStreamReader(new FileInputStream(e.getValue()),"utf-8");
						ps.setCharacterStream(1, ir, (int)e.getValue().length());
						int rowsUpdated = ps.executeUpdate();
						System.out.println(rowsUpdated + "  " + s.toString());
						ir.close();
					}
				}
			}
		}
		br.close();
	}
	
	private class Column {
		protected String columnName;
		protected Integer columnType;
		
		public Column(String columnName, Integer columnType) {
			this.columnName = columnName;
			this.columnType = columnType;
		}
	}
}
