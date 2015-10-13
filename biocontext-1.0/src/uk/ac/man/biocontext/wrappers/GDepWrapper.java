package uk.ac.man.biocontext.wrappers;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.biocontext.tools.SentenceSplitter;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipe.VerbosityLevel;

import martin.common.InputStreamDumper;
import martin.common.Pair;

public class GDepWrapper extends Annotator {
	private InputStreamReader inStream;
	private OutputStreamWriter outStream;
	private Process p;
	private boolean debug;
	private Thread errDumper;
	private File gdepDir;
	public static final String[] outputFields = new String[]{"id","gdep_data"};

	@Override
	public void init(java.util.Map<String,String> data) {
		if (data == null){
			System.err.println("The Gdep wrapper needs a 'gdep' parameter; send with --params gdep=<gdep directory>");
			System.exit(0);
		}

		this.gdepDir = new File(data.get("gdep"));
		this.debug = data.containsKey("debug");
		
		startGDep();
	}
	
	private void startGDep(){
		try{
			if (this.p != null)
				p.destroy();
			
			this.p = Runtime.getRuntime().exec(gdepDir.getAbsolutePath() + "/gdep", null, gdepDir);
			this.errDumper = new Thread(new InputStreamDumper(p.getErrorStream(),System.err));
			this.errDumper.start();

			this.inStream = new InputStreamReader(p.getInputStream());
			this.outStream = new OutputStreamWriter(p.getOutputStream());

			Thread.sleep(10000);
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}	
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String text = data.get("doc_text");
		String docID = data.get("doc_id");

	
		text = text.replace("-", " - ");
		text = text.replace("/", " / ");
		
		SentenceSplitter ssp = new SentenceSplitter(text);

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		int c = 0;

		//for each sentence in the text
		for (Pair<Integer> sc : ssp){
			List<Pair<Integer>> splitSentences = new ArrayList<Pair<Integer>>();
			splitSentences.add(sc);

			/*
			//check if the sentence is too long for Gdep
			boolean tooLong = sc.getY() - sc.getX() > maxLineLength;

			while (tooLong){
				tooLong = false;
				for (int i = 0; i < splitSentences.size(); i++)
					if (splitSentences.get(i).getY() - splitSentences.get(i).getX() > maxLineLength){
						//split the sentence into smaller parts
						tooLong = true;
						int x = splitSentences.get(i).getX();
						int y = splitSentences.remove(i).getY();
						int middle = (y-x)/2 + x;
						splitSentences.add(i++, new Pair<Integer>(x,middle));
						splitSentences.add(i++, new Pair<Integer>(middle,y));
					}
			}
			*/
			
			if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0){
				System.out.println(sc.toString());
				System.out.println(text.substring(sc.getX(), sc.getY()));
			}

			for (Pair<Integer> sd : splitSentences){ 
				StringBuffer sb = new StringBuffer();

				String s = text.substring(sd.getX(), sd.getY());
				s = s.replace("\n", " ");
				s = s.replace("\"", " ");

				boolean process = false;
				for (int i = 0; i < s.length(); i++)
					if (Character.isLetterOrDigit(s.charAt(i))){
						process = true;
						break;
					}

				if (process){
					if (debug)
						System.out.println(">'" + s + "'");

					try{
						outStream.write(s + "\n");
						outStream.flush();
					} catch (Exception e){
						System.err.println("error\tGdep crash\t" + docID + "\t" + e.toString());
						System.err.println("Restarting gdep...");
						startGDep();
						System.err.println("Done, returning.");
						return res;
					}

					String in = readLine(inStream);

					while (in.length() > 0){
						String[] infs = in.split("\\t");
						if (!s.contains(infs[1])){
							System.err.println("Mismatching input/output!");
							System.err.println("Input: " + s);
							System.err.println("Output: " + in);
							System.err.println("Doc: " + docID);
							System.exit(-1);
						}
						sb.append(in + "\n");
						in = readLine(inStream);
					}
				}

				Map<String,String> m = new HashMap<String,String>();
				m.put("gdep_data",sb.toString());
				m.put("id", ""+c++);
				res.add(m);
			}
		}

		return res;
	}

	public static String readLine(InputStreamReader is){
		try {
			StringBuffer sb = new StringBuffer();

			int c = is.read();

			while (c != -1 && c != '\n'){
				sb.append((char)c);
				c = is.read();
			}

			return sb.toString();

		} catch (IOException e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return null;
	}

	@Override
	public void destroy() {
		System.out.println("Closing GDep process.");
		try{
			this.outStream.close();
			this.inStream.close();
			this.p.destroy();
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}
}