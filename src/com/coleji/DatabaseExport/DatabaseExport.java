package com.coleji.DatabaseExport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import com.coleji.Database.OracleConnectionManager;


public class DatabaseExport {
	public static void main(String[] args) {
		try {
			Connection c = new OracleConnectionManager("/home/jcole/property-files/CBI_DEV").getConnection();
			ResultSet tablesRS = c.getMetaData().getTables(null, "CBI_DEV", null, new String[] {"TABLE"});
			ResultSetMetaData tablesRSMD = tablesRS.getMetaData();
			int columnCt = tablesRSMD.getColumnCount();
			for (int i=0; i<columnCt; i++) {
				System.out.print(tablesRSMD.getColumnLabel(i+1) + "\t");
			}
			System.out.println("\n");
			while (tablesRS.next()) {
				System.out.print(tablesRS.getString(3) + "\t");
				System.out.print("\n");
			}
			
			c.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
