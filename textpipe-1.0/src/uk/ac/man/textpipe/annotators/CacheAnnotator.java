package uk.ac.man.textpipe.annotators;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.textpipe.Annotator;

import martin.common.ArgParser;
import martin.common.Misc;
import martin.common.SQL;

public class CacheAnnotator extends Annotator {
	private Annotator annotator;
	private Connection conn;
	private String table;
	private String[] outputFields;
	private PreparedStatement preparedDeleteStatement;
	private PreparedStatement preparedInsertStatement;
	private PreparedStatement preparedSelectStatement;
	private boolean replace;
	private ArgParser ap;
	private String db;
	private boolean readonly;
	private boolean debug;

	public CacheAnnotator(Annotator annotator, String db, String table, ArgParser ap){
		this.annotator = annotator;
		this.table = table;

		this.replace = ap.containsKey("replace");
		this.readonly = ap.containsKey("readOnly");
		this.debug = ap.containsKey("debug");

		this.outputFields = annotator.getOutputFields();
		outputFields = removeDocId(outputFields);

		this.ap = ap;
		this.db = db;

		this.conn = SQL.connectMySQL(ap, null, db);

		if (ap.containsKey("clearCache"))
			clearCache();

		prepareStatements();		
	}

	private String[] removeDocId(String[] arr) {
		boolean contains = false;
		for (String a : arr)
			if (a.equals("doc_id"))
				contains = true;
		if (!contains)
			return arr;

		String[] res = new String[arr.length-1];
		int i = 0;
		for (String a : arr)
			if (!a.equals("doc_id"))
				res[i++] = a;

		return res;
	}

	private void clearCache() {
		try{
			conn.createStatement().execute("DROP TABLE IF EXISTS " + table);

			String q = "CREATE TABLE  " + table + " ("+
			"doc_id VARCHAR(255) NOT NULL,";

			for (String o : outputFields)
				q += o + " TEXT,";

			q += " KEY doc_id (doc_id) ) ENGINE=MyISAM DEFAULT CHARSET=utf8;";

			conn.createStatement().execute(q);
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	private void prepareStatements() {
		try{
			String q = "SELECT " +  Misc.implode(outputFields, ",") + " from " + table + " WHERE doc_id = ?";
			this.preparedSelectStatement = conn.prepareStatement(q);

			q = "INSERT INTO " + table + " (doc_id, " + Misc.implode(outputFields, ",") + ") VALUES (?" + Misc.replicateString(",?", outputFields.length) + ")";
			this.preparedInsertStatement = conn.prepareStatement(q);

			q = "DELETE FROM " + table + " WHERE doc_id = ?";
			this.preparedDeleteStatement = conn.prepareStatement(q);
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public List<List<Map<String, String>>> process(List<Map<String, String>> data) {
		List<List<Map<String, String>>> completeRes = new ArrayList<List<Map<String,String>>>(data.size());

		try{

			for (Map<String,String> m : data){
				String doc_id = m.get("doc_id");
				List<Map<String, String>> res;

				if (doc_id != null){
					SQL.set(preparedSelectStatement, 1, doc_id);
					ResultSet rs = preparedSelectStatement.executeQuery();

					if (rs.isBeforeFirst() && !replace){
						res = retrieve(rs);
					} else if (!readonly) {
						throw new IllegalStateException("You can't use caching and batching without also specifying --readOnly");
					} else {
						return annotator.process(data);
					}

					rs.close();
				} else {
					return annotator.process(data);
				}

				completeRes.add(res);
			}


		} catch (SQLException e){
			if (e.toString().contains("Communications link failure") || e.toString().contains("The last packet successfully received from the server")){
				System.err.println(e.toString());
				System.err.println("Reconnecting to SQL database...");
				this.conn = SQL.connectMySQL(ap, null, db);
				prepareStatements();
				return process(data);
			} else {
				throw new RuntimeException(e);
			}
		}

		return completeRes;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String doc_id = data.get("doc_id");

		try{
			List<Map<String, String>> res;

			if (doc_id != null){
				SQL.set(preparedSelectStatement, 1, doc_id);
				ResultSet rs = preparedSelectStatement.executeQuery();

				if (rs.isBeforeFirst() && !replace){
					res = retrieve(rs);
				} else if (!readonly) {
					res = annotator.process(data);
					save(res, doc_id);
				} else {
					res = annotator.process(data);
				}

				rs.close();
			} else {
				res = annotator.process(data);
			}

			return res;
		} catch (SQLException e){
			if (e.toString().contains("Communications link failure") || e.toString().contains("The last packet successfully received from the server")){
				System.err.println(e.toString());
				System.err.println("Reconnecting to SQL database...");
				this.conn = SQL.connectMySQL(ap, null, db);
				prepareStatements();
				return process(data);
			} else {
				throw new RuntimeException(e);
			}
		}
	}

	private List<Map<String, String>> retrieve(ResultSet rs) {
		try{
			List<Map<String,String>> res = new ArrayList<Map<String,String>>();

			while (rs.next()){
				Map<String,String> m = new HashMap<String, String>();

				int i = 1;
				for (String k : outputFields)
					m.put(k, rs.getString(i++));

				res.add(m);
			}


			if (res.size() > 1)
				return res;

			for (String k : res.get(0).keySet())
				if (!k.equals("doc_id") && res.get(0).get(k) != null)
					return res;

			//We have reached a state where res contains a single row, with all null.
			//This represents an empty result set, so return an empty list.
			return new ArrayList<Map<String,String>>(0);
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	private List<Map<String, String>> save(List<Map<String,String>> data, String doc_id) {
		try{
			SQL.set(preparedDeleteStatement, 1, doc_id);
			preparedDeleteStatement.execute();			

			for (Map<String,String> m : data){
				int i = 2;
				SQL.set(preparedInsertStatement,1,doc_id);
				for (String k : outputFields)
					SQL.set(preparedInsertStatement,i++,m.get(k));

				preparedInsertStatement.addBatch();
			}

			if (data.size() == 0){
				SQL.set(preparedInsertStatement,1,doc_id);
				for (int i = 0; i < outputFields.length; i++)
					SQL.set(preparedInsertStatement,i+2,(String)null);

				preparedInsertStatement.addBatch();
			}

			preparedInsertStatement.executeBatch();

			return data;		

		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	@Override
	public void destroy() {
		this.annotator.destroy();
	}

	@Override
	public String helpMessage() {
		return annotator.helpMessage();
	}

	@Override
	public boolean isConcurrencySafe() {
		return false;
	}
}
