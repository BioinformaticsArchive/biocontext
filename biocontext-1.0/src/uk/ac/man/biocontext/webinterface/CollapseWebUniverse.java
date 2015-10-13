package uk.ac.man.biocontext.webinterface;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import uk.ac.man.biocontext.util.annotators.UniverseWrapper;

import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.Misc;
import martin.common.Pair;
import martin.common.SQL;
import martin.common.Tuple;

public class CollapseWebUniverse {

	static final String[] columns = UniverseWrapper.getHashedFieldsWeb();


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = ArgParser.getParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);

		java.sql.Connection conn = SQL.connectMySQL(ap, logger, "farzin");
		String where = ap.get("where");
		String limit = ap.get("limit");

		//Tuple<Map<hash, broken down hash string (columns)> , Map<hash, doc level count of hash>>
		Tuple<Map<Integer, List<String>>, Map<Integer, Pair<Integer>>> data = null;

		if (ap.containsKey("collapse")){
			String table = ap.get("collapse","data_universe");
			System.out.println("Collapsing universe data (" + table  + ")... ");
			String q = "select hash_web, negated, hash_web_string from " + table + " where type is not null";
			data = collapseData(conn, q, where, limit);
			System.out.println("Done. Read data for " + data.getA().size() + " hashes.");
		}

		if (ap.containsKey("collapseHomology")){
			String table = ap.get("collapseHomology","data_universe");
			System.out.println("Collapsing universe data (" + table  + ") for homology... ");
			String q = "select hash_homologene, negated, hash_homologene_string from " + table + " where type is not null and hash_homologene is not null";
			data = collapseData(conn, q, where, limit);
			System.out.println("Done. Read data for " + data.getA().size() + " hashes.");
		}

		if (ap.containsKey("store")){
			System.out.print("Loading gene descriptions...");
			Map<String,String> geneDescs = Misc.loadMap(ap.getFile("geneDescs"));
			System.out.println(" Done. Loaded " + geneDescs.size() + " descriptions.");
			System.out.print("Loading anatomical descriptions...");
			Map<String,String> anatDescs = Misc.loadMap(ap.getFile("anatDescs"));
			System.out.println(" Done. Loaded " + anatDescs.size() + " descriptions.");

			System.out.println("Storing... ");
			store(data, conn, ap.get("store"), geneDescs, anatDescs);
			System.out.println("Done. Stored data for " + data.getA().size() + " hashes.");
		}
	}

	private static void store(Tuple<Map<Integer, List<String>>, Map<Integer, Pair<Integer>>> data,java.sql.Connection conn, String table, Map<String, String> geneDescs, Map<String, String> anatDescs) {

		//		List<String> columns = Arrays.asList(CollapseWebUniverse.columns);
		List<String> columns = new ArrayList<String>();
		for (String s : CollapseWebUniverse.columns)
			columns.add(s);
		columns.add("anatomy_entity_desc");
		columns.add("c_c_desc");
		columns.add("t_t_desc");

		try{
			Statement stmt = conn.createStatement();

			String q = "DROP TABLE IF EXISTS `" + table + "`;";
			stmt.execute(q);

			q = "CREATE TABLE `" + table + "` ("+
			"`hash` INTEGER NOT NULL,"+
			"`count` INTEGER UNSIGNED NOT NULL,"+
			"`count_neg` INTEGER UNSIGNED NOT NULL,";

			for (String k : columns)
				q += "`" + k + "` VARCHAR(512),";
			for (String k : columns)
				if (!k.endsWith("_desc"))
					q += " KEY `" + k + "` (`" + k + "`),";

			q += "PRIMARY KEY (`hash`)) ENGINE = MyISAM;";

			stmt.execute(q);

			q = "INSERT INTO " + table + " (hash,count,count_neg";
			for (int i = 0; i < columns.size(); i++)
				q += "," + columns.get(i);
			q += ") VALUES (?,?,?" + Misc.replicateString(",?", columns.size()) + ")";
			PreparedStatement pstmt = conn.prepareStatement(q);

			Map<Integer,Pair<Integer>> counts = data.getB();
			Map<Integer,List<String>> fields = data.getA();

			for (Integer key : counts.keySet()){
				String anat = fields.get(key).get(1);
				String c_c = fields.get(key).get(2);
				String t_t = fields.get(key).get(3);

				if (anat.length() < 512 && c_c.length() < 512 && t_t.length() < 512){
					SQL.set(pstmt, 1, key);
					SQL.set(pstmt, 2, counts.get(key).getX());
					SQL.set(pstmt, 3, counts.get(key).getY());
					int c = 4;
					for (String v : fields.get(key))
						SQL.set(pstmt, c++, v);


					if (!anat.equals("-") && anatDescs.get(anat) != null)
						SQL.set(pstmt, c++, anatDescs.get(anat));
					else
						SQL.set(pstmt,c++,(String)null);

					if (!c_c.equals("-") && geneDescs.get(c_c) != null)
						SQL.set(pstmt, c++, geneDescs.get(c_c));
					else
						SQL.set(pstmt,c++,(String)null);

					if (!t_t.equals("-") && geneDescs.get(t_t) != null)
						SQL.set(pstmt, c++, geneDescs.get(t_t));
					else
						SQL.set(pstmt,c++,(String)null);

					pstmt.execute();	
				}
			}
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	private static Tuple<Map<Integer, List<String>>, Map<Integer, Pair<Integer>>> collapseData(
			java.sql.Connection conn, String q, String where, String limit) {

		if (where != null)
			q += " and (" + where + ")";

		if (limit != null){
			q += " LIMIT " + limit;
		}

		Map<Integer,Pair<Integer>> counts = new HashMap<Integer,Pair<Integer>>();
		Map<Integer,List<String>> values = new HashMap<Integer,List<String>>();


		try{
			Statement stmt = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchSize(Integer.MIN_VALUE);
			ResultSet rs = stmt.executeQuery(q);

			while (rs.next()){
				Integer hash = Integer.parseInt(rs.getString(1));
				String negated = rs.getString(2);

				if (!counts.containsKey(hash)){
					counts.put(hash,new Pair<Integer>(0,0));

					String hash_string = rs.getString(3);
					List<String> arr = new ArrayList<String>();
					for (String v : Arrays.asList(hash_string.split("\t",-1)))
						//						if (!v.equals("-"))
						arr.add(v);
					//						else
					//							arr.add(null);

					values.put(hash,arr);
				}

				Pair<Integer> c = counts.get(hash);

				if (negated != null && negated.equals("1"))
					c.setY(c.getY()+1);
				else
					c.setX(c.getX()+1);
			}

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return new Tuple<Map<Integer, List<String>>, Map<Integer, Pair<Integer>>>(values,counts);
	}
}
