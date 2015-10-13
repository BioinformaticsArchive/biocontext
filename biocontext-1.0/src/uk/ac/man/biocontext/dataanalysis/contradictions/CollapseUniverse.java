package uk.ac.man.biocontext.dataanalysis.contradictions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import uk.ac.man.biocontext.util.annotators.UniverseWrapper;

import martin.common.ArgParser;
import martin.common.ComparableTuple;
import martin.common.Loggers;
import martin.common.Misc;
import martin.common.Pair;
import martin.common.SQL;
import martin.common.Tuple;

public class CollapseUniverse {

	static final int indexOfNegation = 1;
	static final String[] columns = UniverseWrapper.getHashedFieldsContradictions();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = ArgParser.getParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);

		java.sql.Connection conn = SQL.connectMySQL(ap, logger, "farzin");
		String where = ap.get("where");
		String limit = ap.get("limit");

		Map<Integer,UniqueEvent> data=null;

		if (ap.containsKey("collapse")){
			System.out.println("Collapsing universe data... ");
			data = collapseData(conn, where, limit);
			System.out.println("Done. Read data for " + data.size() + " hashes.");
		}

		if (ap.containsKey("load")){
			System.out.println("Loading... ");
			data = load(conn,ap.get("load"),where, limit);
			System.out.println("Done. Read data for " + data.size() + " hashes.");
		}

		if (ap.containsKey("store")){
			System.out.println("Storing... ");
			store(data, conn, ap.get("store"));
			System.out.println("Done. Stored data for " + data.size() + " hashes.");
		}

		if (ap.containsKey("contradictions")){
			System.out.println("Calculating contradictions...");
			List<Pair<Integer>> contradictions = contradictions(data);
			System.out.println("Done. Detected " + contradictions.size() + " contradictions.");

			if (ap.containsKey("countTypes")){
				System.out.println("Counting...");
				countTypes(contradictions, data);
			}

			if (ap.containsKey("sentences")){
				System.out.println("Printing sentences...");
				printContradictorySentences(conn, ap.getInt("sentences"), contradictions, data);
			}

			if (ap.containsKey("dumpContradictions")){
				System.out.println("Dumping...");
				dumpContradictions(contradictions, data, ap.getFile("dumpContradictions"));
			}			
		}

		if (ap.containsKey("contrasts")){
			//			contrasts(data, conn, ap.getInt("sentences",-1));
		}
	}

	private static void dumpContradictions(List<Pair<Integer>> contradictions,
			Map<Integer, UniqueEvent> data, File file) {
		try{
			BufferedWriter outStream = new BufferedWriter(new  FileWriter(file));
			outStream.write("#type\tpositive hash\tnegative hash\tpositive count\tpositive confidence sum\tnegative count\tnegative confidence sum\n");

			for (Pair<Integer> p : contradictions){
				outStream.write(data.get(p.getX()).getData().get(0) + "\t");
				outStream.write(p.getX() + "\t" + p.getY() + "\t");
				outStream.write(data.get(p.getX()).getCountDocs() + "\t");
				outStream.write(data.get(p.getX()).getSumConfidences() + "\t");
				outStream.write(data.get(p.getY()).getCountDocs() + "\t");
				outStream.write(data.get(p.getY()).getSumConfidences() + "\t");
				outStream.write("\n");
			}

			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

	}

	private static void printContradictorySentences(Connection conn, Integer sentences, List<Pair<Integer>> contradictions, Map<Integer, UniqueEvent> data) {
		try{
			PreparedStatement pstmt=prepareSentenceStmt(conn, sentences);

			Map<String,Integer> counts = new HashMap<String,Integer>();
			
			for (int i = 0; i < contradictions.size(); i++){
				Pair<Integer> p = contradictions.get(i);
				String type = data.get(p.getX()).getData().get(0);
				if (!counts.containsKey(type))
					counts.put(type,0);

				if (counts.get(type) < 50){
					System.out.print("<b>" + type + " (" + counts.get(type) + "), " + contradictions.get(i).toString() + "</b><br>");

					int positiveHash = contradictions.get(i).getX();
					int negativeHash = contradictions.get(i).getY();
					
					if (pstmt != null){
						System.out.print("<b>Positive</b><br><ul>");
						printSentences(pstmt,positiveHash);
						System.out.print("</ul><b>Negative</b><br><ul>");
						printSentences(pstmt,negativeHash);
						System.out.println("</ul><hr>");
					}
				}

				counts.put(type,counts.get(type)+1);
			}
			
			pstmt.close();
			
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(0);
		}
	}

	private static void countTypes(List<Pair<Integer>> contradictions, Map<Integer, UniqueEvent> data) {
		Map<String,Integer> counts = new HashMap<String,Integer>();
		for (Pair<Integer> p : contradictions){
			String type = data.get(p.getX()).getData().get(0);
			if (!counts.containsKey(type))
				counts.put(type,0);
			counts.put(type,counts.get(type)+1);
		}

		for (String k : counts.keySet())
			System.out.println(k + "\t" + counts.get(k));
		System.out.println();
		System.out.println("Total\t" + contradictions.size());
	}

	private static Map<Integer, UniqueEvent> load(
			Connection conn, String table, String where, String limit) {

		Map<Integer,UniqueEvent> res = new HashMap<Integer,UniqueEvent>();


		try{
			String q = "SELECT * from " + table;
			if (where != null)
				q += " WHERE " + where;

			if (limit != null){
				q += " LIMIT " + limit;
			}

			Statement stmt = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchSize(Integer.MIN_VALUE);
			ResultSet rs = stmt.executeQuery(q);


			while (rs.next()){
				int hash = rs.getInt(1);
				int count = rs.getInt(2);
				double confidence = rs.getDouble(3);

				List<String> localfields = new ArrayList<String>();

				for (int i = 0; i < columns.length; i++){
					localfields.add(rs.getString(i+4));
				}

				res.put(hash,new UniqueEvent(localfields,count,confidence,hash));
			}

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		

		return res;
	}

	private static void store(
			Map<Integer, UniqueEvent> data, java.sql.Connection conn, String table) {

		try{
			Statement stmt = conn.createStatement();

			String q = "DROP TABLE IF EXISTS `" + table + "`;";
			stmt.execute(q);

			q = "CREATE TABLE `" + table + "` ("+
			"`hash` INTEGER NOT NULL,"+
			"`count` INTEGER UNSIGNED NOT NULL,"+
			"`sum_confidence` DOUBLE UNSIGNED NOT NULL,";

			for (String k : columns)
				q += "`" + k + "` VARCHAR(255), KEY `" + k + "` (`" + k + "`),";
			q += "PRIMARY KEY (`hash`)) ENGINE = MyISAM;";

			stmt.execute(q);

			q = "INSERT INTO " + table + " (hash,count,sum_confidence";
			for (int i = 0; i < columns.length; i++)
				q += "," + columns[i];
			q += ") VALUES (?,?,?" + Misc.replicateString(",?", columns.length) + ")";
			PreparedStatement pstmt = conn.prepareStatement(q);

			for (Integer key : data.keySet()){
				SQL.set(pstmt, 1, key);
				SQL.set(pstmt, 2, data.get(key).getCountDocs());
				SQL.set(pstmt, 3, data.get(key).getSumConfidences());
				int c = 4;
				for (String v : data.get(key).getData())
					SQL.set(pstmt, c++, v);

				pstmt.execute();				
			}
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	private static int getHash(String[] values){
		String[] v_copy = values.clone();
		for (int i = 0; i < v_copy.length; i++)
			if (v_copy[i] == null)
				v_copy[i] = "-";
		String v = Misc.implode(v_copy, "\t");
		return v.hashCode();
	}

	private static void contrasts(Tuple<Map<Integer, List<String>>, Map<Integer, Integer>> data, Connection conn, int sentences){

		//data: Tuple<Map<hash, broken down hash string (columns)> , Map<hash, doc level count of hash>>
		System.out.println("Running contrasts...");
		List<Pair<Integer>> contrastingHashes = getContrastingHashes(data);

		System.out.println("Done. Detected " + contrastingHashes.size() + " contrasting pairs.");

		PreparedStatement pstmt = prepareSentenceStmt(conn, 3);

		//		for (Pair<Integer> cPair : contrastingHashes){
		for (int i = 0; i < 20; i++){
			Pair<Integer> cPair = contrastingHashes.get(i);
			System.out.print(data.getB().get(cPair.getX()) + "  ");
			System.out.print(data.getB().get(cPair.getY()) + "  ");
			//			System.out.print(Math.min(data.getB().get(cPair.getX()),data.getB().get(cPair.getY())));
			System.out.println(cPair);
			if (sentences != -1){
				printSentences(pstmt, cPair.getX());
				System.out.println();
				printSentences(pstmt, cPair.getY());
			}
		}
	}

	private static List<Pair<Integer>> getContrastingHashes(
			final Tuple<Map<Integer, List<String>>, Map<Integer, Integer>> data) {

		Map<Integer, List<String>> fields = data.getA();

		List<Pair<Integer>> contrastingHashes = new ArrayList<Pair<Integer>>();

		int[] relevantFields = new int[]{
				0,
				5,
				12,
				3,
				8,
				9,
				10,
				15,
				16,
				17,
		};

		String[] hashStrings = new String[fields.size()];
		int[] hashes = new int[fields.size()];
		Integer[] indices = new Integer[fields.size()];
		boolean[] negated = new boolean[fields.size()];

		{
			int i = 0; 
			for (int k : fields.keySet()){
				hashes[i] = k;
				indices[i] = i;
				negated[i] = fields.get(k).get(indexOfNegation).equals("1");
				i++;
			}
		}

		List<List<Integer>> affirmativeList = new ArrayList<List<Integer>>();
		List<List<Integer>> negatedList = new ArrayList<List<Integer>>();

		int total = 0;

		for (int field = 3; field < relevantFields.length; field++){
			System.out.println("Entering field " + field + " with " + total + " detected contrasting pairs.");

			hashStrings = getHashStrings(hashes,fields,relevantFields,field);

			sort(indices,hashStrings);

			for (int i = 0; i < indices.length; i++){
				//			System.out.print(i + "\t" + hashes[indices[i]]);
				int s1 = getColumnStart(hashStrings[indices[i]]);
				//			System.out.print("\t" + s1);
				String prefix = hashStrings[indices[i]].substring(0,s1+1);
				//			System.out.print("\t'" + prefix + "'");


				List<Integer> affirmativeLocal = new ArrayList<Integer>(); 
				List<Integer> negatedLocal = new ArrayList<Integer>(); 


				if (!hashStrings[indices[i]].endsWith("-")){
					if (negated[indices[i]])
						negatedLocal.add(indices[i]);
					else
						affirmativeLocal.add(indices[i]);
				}

				while (i+1 < indices.length && hashStrings[indices[i+1]].startsWith(prefix)){
					i++;
					if (!hashStrings[indices[i]].endsWith("-")){
						if (negated[indices[i]])
							negatedLocal.add(indices[i]);
						else
							affirmativeLocal.add(indices[i]);
					}
				}

				if (affirmativeLocal.size() > 0 && negatedLocal.size() > 0){
					affirmativeList.add(affirmativeLocal);
					negatedList.add(negatedLocal);
					total += affirmativeLocal.size() * negatedLocal.size();

					for (int index : affirmativeLocal)
						System.out.println(hashStrings[index].substring(s1));
					System.out.println();
					for (int index : negatedLocal)
						System.out.println(hashStrings[index].substring(s1));
					System.out.println("---");

				}

				/*
				for (int j = i+1; j < indices.length; j++){
//					System.out.println(i + ", " + j);
					if (hashStrings[indices[j]].startsWith(prefix)){
						if (neg != negated[indices[j]] && !hashStrings[indices[i]].endsWith("-") && !hashStrings[indices[j]].endsWith("-")){
							//					System.out.print("\t->" + j + "<-");
//							if (x++ < 3){
								contrastingHashes.add(new Pair<Integer>(hashes[indices[i]],hashes[indices[j]]));
//								System.out.println(hashes[indices[i]] + ": " + hashStrings[indices[i]] + " --- " + hashes[indices[j]] + ": " + hashStrings[indices[j]]);
//							}
								if (contrastingHashes.size() % 10000 == 0)
									System.out.println(field + "\t" + i + "\t" + j + "\t" + contrastingHashes.size());
						}
					} else {
						break;
					}
				}
				 */
				//			System.out.println();
			}
		}


		for (int i = 0; i < affirmativeList.size(); i++){
			//			for (Integer index : affirmativeList.get(i)){
			//				System.out.println(negated[index] + "\t" + hashes[index] + "\t" + hashStrings[index]);
			//			}
			//			for (Integer index : negatedList.get(i)){
			//				System.out.println(negated[index] + "\t" + hashes[index] + "\t" + hashStrings[index]);
			//			}
		}

		System.out.println("Total: " + total);



		System.exit(0);



		System.out.println("Done. Sorting contrasting pairs...");



		Collections.sort(contrastingHashes, new Comparator<Pair<Integer>>(){
			@Override
			public int compare(Pair<Integer> o1, Pair<Integer> o2) {
				int v1_1 = data.getB().get(o1.getX());
				int v1_2 = data.getB().get(o1.getY());
				int v2_1 = data.getB().get(o2.getX());
				int v2_2 = data.getB().get(o2.getY());
				int v1 = Math.min(v1_1,v1_2);
				int v2 = Math.min(v2_1,v2_2);
				//						int v1 = v1_1+v1_2;
				//						int v2 = v2_1+v2_2;
				return - (new Integer(v1).compareTo(v2));
			}
		});

		System.out.println("Done.");

		return contrastingHashes;
	}

	private static int getColumnStart(String string) {
		int s = 0;
		while (string.indexOf('\t',s+1) != -1)
			s = string.indexOf('\t',s+1);
		return s;			
	}

	private static void sort(Integer[] indices, final String[] hashStrings) {
		Arrays.sort(indices,new Comparator<Integer>(){

			@Override
			public int compare(Integer o1, Integer o2) {
				return hashStrings[o1].compareTo(hashStrings[o2]);
			}
		});
	}

	private static String[] getHashStrings(int[] hashes, Map<Integer, List<String>> fields, int[] relevantFields, int column) {
		String[] res = new String[hashes.length];

		for (int i = 0 ; i < hashes.length; i++){
			List<String> fields_ = fields.get(hashes[i]);
			String v = "";
			for (int j = 0; j < relevantFields.length; j++){
				int c = (j+column+1)%relevantFields.length;
				if (j > 0)
					v += "\t" + relevantFields[c] + ":" + fields_.get(relevantFields[c]);
				else
					v += relevantFields[c] + ":" + fields_.get(relevantFields[c]);
			}
			res[i] = v;
		}

		return res;
	}

	private static boolean contrastsWith(List<String> list, List<String> list2) {
		if (list.size() != list2.size()){
			return false;
		}

		int distance = 0;
		int i = 0;

		if (list.get(indexOfNegation).equals(list2.get(indexOfNegation))){
			return false;
		}

		while (distance < 3 && i < list.size()){
			if (!list.get(i).equals(list2.get(i))){
				distance++;
			}
			i++;
		}
		if (distance == 2){
			return true;
		}

		return false;
	}

	private static List<Pair<Integer>> contradictions(Map<Integer, UniqueEvent> data) {
//		for (Integer k : data.keySet()){
//			String[] vs = data.get(k).getData().toArray(new String[0]);
//			int k2 = getHash(vs);
//			if (k != k2)
//				throw new IllegalStateException(k + " ---'" + vs.toString() + "'--- " + k2);
//		}

		ComparableTuple[] a = new ComparableTuple[data.size()];
		int c = 0;
		for (int k : data.keySet())
			a[c++] = new ComparableTuple<Integer, Integer>(data.get(k).getCountDocs(),k);
		Arrays.sort(a);

		List<ComparableTuple<Double,String>> res = new ArrayList<ComparableTuple<Double,String>>();

		for (int i = a.length-1; i>0; i--){
			int h = (Integer) a[i].getB();
			String[] l = data.get(h).getData().toArray(new String[0]);
			if (l[1] == null || l[1].equals("0")){
				l[1] = "1";
				int h2 = getHash(l);
				//			int h2 = Misc.implode(l, "\t").hashCode();
				if (data.containsKey(h2)){
					//				System.out.println("\n\n" + h + " (" + counts.get(h) + ")\t" + h2 + " (" + counts.get(h2) + ")\n\n");
					String s = h + "," + h2;
//					int x = Math.min(data.get(h).getCountDocs(),data.get(h2).getCountDocs());
					double confidence = Math.min(data.get(h).getSumConfidences(),data.get(h2).getSumConfidences());
					res.add(new ComparableTuple<Double, String>(-confidence,s));
				}
			}
		}

		Collections.sort(res);

		List<Pair<Integer>> res2 = new ArrayList<Pair<Integer>>(res.size());
		for (ComparableTuple<Double,String> ct : res){
			Integer affirmative = Integer.parseInt(ct.getB().split(",")[0]);
			Integer negated = Integer.parseInt(ct.getB().split(",")[1]);
			res2.add(new Pair<Integer>(affirmative,negated));
		}		

		return res2;
	}

	private static void printSentences(PreparedStatement pstmt, int hash) {
		SQL.set(pstmt,1,""+hash);
		try{
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()){
				System.out.print("<li>" + rs.getString(2) + ": " + rs.getString(1).replace('\n', ' ') + "<br>");
			}
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(0);
		}
	}

	private static PreparedStatement prepareSentenceStmt(Connection conn, int sentences){
		try{
			PreparedStatement pstmt=null;
			if (sentences != -1){
				pstmt = conn.prepareStatement("select sentence_html,doc_id from data_universe where hash_contradictions=? order by confidence desc limit " + sentences);
			}

			return pstmt;
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);

		}
		return null;		
	}

	private static Map<Integer,UniqueEvent> collapseData(
			java.sql.Connection conn, String where, String limit) {

		String q = "select hash_contradictions, hash_contradictions_string, doc_id,confidence from data_universe where type is not null";
		if (where != null)
			q += " and (" + where + ")";

		if (limit != null){
			q += " LIMIT " + limit;
		}

		Map<Integer,UniqueEvent> res = new HashMap<Integer,UniqueEvent>();

		Map<Integer,Set<String>> documents = new HashMap<Integer,Set<String>>();

		Map<Integer,Map<String,TreeSet<Double>>> confidences = new HashMap<Integer,Map<String,TreeSet<Double>>>();

		try{
			Statement stmt = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchSize(Integer.MIN_VALUE);
			ResultSet rs = stmt.executeQuery(q);

			while (rs.next()){
				Integer hash = Integer.parseInt(rs.getString(1));
				String docid = rs.getString(3);
				double confidence = rs.getDouble(4);
				if (!res.containsKey(hash)){
					String values = rs.getString(2);
					List<String> arr = new ArrayList<String>();
					for (String v : Arrays.asList(values.split("\t",-1)))
						arr.add(v);

					res.put(hash, new UniqueEvent(arr, 0, 0, hash));
					documents.put(hash, new HashSet<String>());
					confidences.put(hash, new HashMap<String,TreeSet<Double>>());
				}
				if (!documents.get(hash).contains(docid)){
					res.get(hash).incrementCountDocs(1);
					documents.get(hash).add(docid);
					confidences.get(hash).put(docid, new TreeSet<Double>());
				}
				confidences.get(hash).get(docid).add(confidence);
			}

			for (Integer hash : confidences.keySet())
				for (String docid : confidences.get(hash).keySet())
					res.get(hash).incrementSumConfidences(confidences.get(hash).get(docid).last());



		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return res;
	}
}
