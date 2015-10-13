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

public class PrecomputedAnnotator extends Annotator {
	private Connection conn;
	private String table;
	private String[] outputFields;
	private PreparedStatement preparedSelectStatement;
	private String db;
	private ArgParser ap;
	private boolean requireComputed;

	public PrecomputedAnnotator(String[] outputFields, String db, String table, ArgParser ap, boolean requireComputed){
		this.table = table;
		this.requireComputed = requireComputed;

		this.ap = ap;

		this.outputFields = outputFields;
		outputFields = removeDocId(outputFields);

		this.db = db;

		this.conn = SQL.connectMySQL(ap, null, db);

		prepareStatements();		
	}

	public PrecomputedAnnotator(String[] outputFields, String db, String table, ArgParser ap){
		this(outputFields,db,table,ap,false);
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

	private void prepareStatements() {
		try{
			String q = "SELECT " +  Misc.implode(outputFields, ",") + " from " + table + " WHERE doc_id = ?";
			this.preparedSelectStatement = conn.prepareStatement(q);
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String doc_id = data.get("doc_id");
		if (doc_id == null)
			throw new IllegalStateException("doc_id cannot be null when calling PrecomputedMatcher.process()");

		try{
			List<Map<String, String>> res;

				SQL.set(preparedSelectStatement, 1, doc_id);
				ResultSet rs = preparedSelectStatement.executeQuery();

					res = retrieve(rs, doc_id);
				rs.close();

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

	private List<Map<String, String>> retrieve(ResultSet rs, String docid) {
		try{
			List<Map<String,String>> res = new ArrayList<Map<String,String>>();

			while (rs.next()){
				Map<String,String> m = new HashMap<String, String>();

				int i = 1;
				for (String k : outputFields)
					m.put(k, rs.getString(i++));

				res.add(m);
			}

			if (res.size() == 0){
				if (requireComputed)
					throw new IllegalStateException("No precomputed data found for document " + docid + " in table " + table);
				else
					return new ArrayList<Map<String,String>>(0);
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

	@Override
	public void destroy() {
		try {
		this.preparedSelectStatement.close();
		this.conn.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
		}
	}

	public boolean isConcurrencySafe() {
		return false;
	}
}
