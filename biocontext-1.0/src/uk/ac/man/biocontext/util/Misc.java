package uk.ac.man.biocontext.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import martin.common.Pair;

import uk.ac.man.biocontext.tools.SentenceSplitter;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.dataholders.Document.Text_raw_type;


public class Misc {
	private static Random r = new Random();

	public static File getRandomTmpDir(){
		return getRandomTmpDir(new File("/tmp/"));
	}

	static public File getRandomTmpDir(File parent){
		File outputDir = null;
		while (outputDir == null || outputDir.exists()){
			outputDir = new File(parent,"farzin-tmp-" + r.nextInt(1000000));
		}
		return outputDir;
	}
	
	public static void increase(Map<String, String> entry, String key, int s) {
		if (entry.get(key) != null){
			int x = Integer.parseInt(entry.get(key)) + s;
			entry.put(key,""+x);
		}		
	}

	public static List<Map<String, String>> doSubDocs(Map<String, String> data, int numSentencesPerSubdoc) {
		String text = data.get("doc_text");
		
		SentenceSplitter ssp = new SentenceSplitter(text);
		
		StringBuffer sb = new StringBuffer();
		int c = 0;
		int x = 0;

		List<String> resTexts = new ArrayList<String>();
		List<String> resIDs = new ArrayList<String>();
		
		String currentID = data.get("doc_id") + "." + (x++) + ".0"; 
		for (Pair<Integer> p : ssp){
			sb.append(text.substring(p.getX(),p.getY()));
			c++;
			if (c == numSentencesPerSubdoc){
				resTexts.add(sb.toString());
				resIDs.add(currentID);
				
				currentID = data.get("doc_id") + "." + (x++) + "." + p.getY();
				sb = new StringBuffer();
				c = 0;
			}
		}
		if (c > 0){
			resTexts.add(sb.toString());
			resIDs.add(currentID);
		}
		
		List<Map<String,String>> res = new ArrayList<Map<String,String>>();
		
		for (int i = 0; i < resTexts.size(); i++){
			Map<String,String> d = clone(data);
			d.put("doc_text", resTexts.get(i));
			d.put("doc_id", resIDs.get(i));
			res.add(d);
		}
		
		return res;
	}

	public static Map<String, String> clone(Map<String, String> data) {
		Map<String,String> res = new HashMap<String,String>();
		for (String k : data.keySet())
			res.put(k,data.get(k));
		return res;
	}



	static public void sort(List<Map<String,String>> list){
		Collections.sort(list, new Comparator<Map<String,String>>(){

			@Override
			public int compare(Map<String, String> o1, Map<String, String> o2) {
				if (o1 == null && o2 == null)
					return 0;
				if (o1 == null)
					return -1;
				if (o2 == null)
					return 1;

				Integer a = Integer.parseInt(o1.get("id"));
				Integer b = Integer.parseInt(o2.get("id"));
				return a.compareTo(b);
			}			
		});		
	}

	static public boolean delete(File path) {
		if ( path.isDirectory() && path.exists() ) {
			File[] files = path.listFiles();
			for(int i=0; i<files.length; i++) {
				if(files[i].isDirectory()) {
					delete(files[i]);
				}
				else {
					files[i].delete();
				}
			}
		}
		
		return( path.delete() );
	}

	static public void printchars(List<Map<String,String>> data){
		for (Map<String,String> m : data)
			for (String v : m.values()){
				System.out.println(v);
				for (int i = 0; i < v.length(); i++)
					System.out.println(i + "\t'" + v.charAt(i) + "'\t" + ((int) v.charAt(i)));
				System.out.println();
			}

	}

	public static File writeTempFile(File tmpDir, String text) {
		File f=null;

		while (f == null || f.exists())
			f = new File(tmpDir,"farzin-tmp-" + r.nextInt(1000000) + ".txt");

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(f));
			outStream.write(text);
			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return f;
	}

	public static Map<String, String> getByID(List<Map<String, String>> data, int id) {
		if (data.size() > id && data.get(id).containsKey("id") && data.get(id).get("id").equals(""+id))
			return data.get(id);

		return getByID(data,""+id);
	}

	public static Map<String, String> getByID(List<Map<String, String>> data, String id) {
		return getByID(data, id, "id");
	}
	
	public static Map<String, String> getByID(List<Map<String, String>> data, String id, String columnName) {
		for (Map<String,String> m : data)
			if (m != null && m.get(columnName) != null && m.get(columnName).equals(id))
				return m;

		return null;
	}


	public static String[] arraySlice(String[] array, int offsetBegin, int offsetEnd) {
		int length = offsetEnd - offsetBegin;
		String[] result = new String[length];
		System.arraycopy(array, offsetBegin, result, 0, length);
		return result;
	}
	public static String[] arraySlice(String[] array, int offset) {
		return arraySlice(array, offset, array.length);
	}

	public static String replicateString(String s, int n){
		String str = new String();

		for (int i = 0; i < n; i++)
			str += s;

		return str;
	}

	/**
	 * Creates a table of name 'name' and with the columns doc_id + 'cols'.
	 * @param conn
	 * @param name
	 * @param cols
	 * @return a prepared insert statement for the table
	 */
	public static PreparedStatement createTable(Connection conn, String name, String[] cols) {
		try{
			String q = "DROP TABLE IF EXISTS " + name + ";";

			conn.createStatement().execute(q);

			q = "CREATE TABLE  " + name + " ("+
			"doc_id VARCHAR(255) NOT NULL,";

			for (String c : cols)
				q += c + " TEXT,";

			q += " KEY doc_id (doc_id) ) ENGINE=MyISAM DEFAULT CHARSET=utf8;";

			conn.createStatement().execute(q);

			q = "INSERT INTO " + name + " VALUES (?" + replicateString(",?", cols.length) + ")";

			return conn.prepareStatement(q);


		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return null;
	}

	public static Document toDoc(Map<String, String> data) {
		return new Document(data.get("doc_id"), null, null,null, data.get("doc_text"), Text_raw_type.TEXT, null, null, null, null, null, null, null, data.get("doc_xml"), null);
	}

	public static void writeFile(File textFile, String text) {
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(textFile));
			outStream.write(text);
			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void checkColumnLengths(Map<String, String> item,
			String[] outputFieldsWithIndices, String docid) {
		
		for (String k : outputFieldsWithIndices){
			if (k.startsWith("@")){
				if (item.get(k.substring(1)) != null && item.get(k.substring(1)).length() > 255)
					throw new IllegalStateException("The value for " + k + " is longer than 255 chars, which is impossible for a key column. value: " + item.get(k.substring(1)) + ", doc: " + docid);
			}
		}		
	}
	
	public static List<Pair<Integer>> getSentenceSplits(String text) {
		SentenceSplitter ssp = new SentenceSplitter(text);
		List<Pair<Integer>> res = new ArrayList<Pair<Integer>>();
		for (Pair<Integer> p : ssp)
			res.add(p);
		return res;
	}
}
