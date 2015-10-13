package uk.ac.man.documentparser.input;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import uk.ac.man.documentparser.dataholders.Document;

public class NCBIEUtilsSearcher implements DocumentIterator {
	private static final String EUTILSEARCH = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
	private static final String EUTILSFETCH = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
	private static final int RETMAX=250;

	private String qk,we;
	private int max;
	private int retstart=0;
	
	private LinkedList<String> fetched = new LinkedList<String>();

	public NCBIEUtilsSearcher(String query){
		try {
			String q = "db=pubmed&usehistory=y&retmax=0&term=" + URLEncoder.encode(query, "UTF-8");

			URL url = new URL(EUTILSEARCH);
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(q);
			wr.flush();
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;

			while ((line = rd.readLine()) != null){
				if (line.matches(".*<Count>.*</Count>.*")){
					int s = line.indexOf("<Count>");
					int e = line.indexOf("</Count>");
					max = Integer.parseInt(line.substring(s+7,e));
				}
				if (line.matches(".*<QueryKey>.*</QueryKey>.*")){
					int s = line.indexOf("<QueryKey>");
					int e = line.indexOf("</QueryKey>");
					qk = line.substring(s+10,e);
				}
				if (line.matches(".*<WebEnv>.*</WebEnv>.*")){

					int s = line.indexOf("<WebEnv>");
					int e = line.indexOf("</WebEnv>");
					we = line.substring(s+8,e);
				}
			}
			
			fetch();

		} catch (Exception e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	public void fetch(){
		String q = "rettype=abstract&retmode=xml&retstart=" + retstart + "&retmax=" + RETMAX + "&db=pubmed&email=martin.gerner@gmail.com&tool=LINNAEUS&query_key=" + qk + "&WebEnv=" + we;
		
		try{
			URL url = new URL(EUTILSFETCH);
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(q);
			wr.flush();
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;

			StringBuffer sb = new StringBuffer();
			while ((line = rd.readLine()) != null){
				sb.append(line);
			}
			
			int s = sb.indexOf("<MedlineCitation");
			while (s != -1){
				int e= sb.indexOf("</MedlineCitation>",s);
				
				String xml = sb.substring(s,e+18);
				
				this.fetched.add(xml);
				
				s = sb.indexOf("<MedlineCitation",e);
			}
			
			retstart += RETMAX;
			
			Thread.sleep(500);
			
		} catch (Exception e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	@Override
	public Document next() {
		if (fetched.size() == 0)
			fetch();
		if (fetched.size() == 0)
			throw new NoSuchElementException();
		
		String xml = MedlineIndex.startXML + fetched.removeFirst() + MedlineIndex.endXML;
		
		DocumentIterator d = new Medline(new StringBuffer(xml));
		return d.next();
	}
	
	@Override
	public void skip() {
		if (fetched.size() == 0)
			fetch();
		if (fetched.size() == 0)
			throw new NoSuchElementException();
		fetched.remove();
	}

	@Override
	public boolean hasNext() {
		return (retstart < max || fetched.size() > 0);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Document> iterator() {
		return this;
	}
}
