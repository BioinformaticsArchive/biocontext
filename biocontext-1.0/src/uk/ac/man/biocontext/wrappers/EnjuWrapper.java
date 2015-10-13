package uk.ac.man.biocontext.wrappers;

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
import martin.common.Misc;
import martin.common.Pair;

public class EnjuWrapper extends Annotator {
	private InputStreamReader inStream;
	private OutputStreamWriter outStream;
	private Process p;
	private boolean debug;
	private Thread errDumper;
	private InputStreamReader errorStream;
	private InputStreamDumper isd;
	private int base = 0;
	public static final String[] outputFields = new String[]{"id","enju_data"};

	@Override
	public void init(java.util.Map<String,String> data) {
		if (data == null){
			System.err.println("The Enju wrapper needs a 'cmd' parameter; send with --params cmd=<enju run command>");
			System.exit(0);
		}
		
		String cmd = data.get("cmd") + " -W 1000 -genia -so";
		System.out.println("Starting Enju: " + cmd);
		
		this.debug = data.containsKey("debug");

		try{
			this.p = Runtime.getRuntime().exec(cmd);

			this.inStream = new InputStreamReader(p.getInputStream());
			this.outStream = new OutputStreamWriter(p.getOutputStream());

			this.errorStream = new InputStreamReader(p.getErrorStream());
			
			String s = GDepWrapper.readLine(errorStream);
			while (!s.equals("Ready")){
				System.out.println(s);
				s = GDepWrapper.readLine(errorStream);
			}

			
			this.isd = new InputStreamDumper(p.getErrorStream(),System.err);
			this.errDumper = new Thread(isd);
			this.errDumper.start();
			
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
		
		if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0){
			System.out.println(data.get("doc_id"));
		}
		
 		SentenceSplitter ssp = new SentenceSplitter(text);

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		int c = 0;

		int nextBase = base;
		
		//for each sentence in the text
		for (Pair<Integer> sc : ssp){
			List<Pair<Integer>> splitSentences = new ArrayList<Pair<Integer>>();
			splitSentences.add(sc);
			
			for (Pair<Integer> sd : splitSentences){ 
				StringBuffer sb = new StringBuffer();

				String s = text.substring(sd.getX(), sd.getY());
				s = s.replace("\n", " ");
				s = s.replace("\"", " ");
				
				if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0){
					System.out.println(s);
				}
				
//				nextBase += s.length();

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
						System.err.println("error\tEnju crash\t" + data.get("doc_id"));
						return new ArrayList<Map<String,String>>();
//						throw new RuntimeException(e);
					}

					String in = GDepWrapper.readLine(inStream);

					while (in.length() > 0){
						String[] infs = in.split("\\t");
						
						nextBase = Math.max(nextBase,Integer.parseInt(infs[1])+1);
						
						infs[0] = "" + (Integer.parseInt(infs[0]) - base);
						infs[1] = "" + (Integer.parseInt(infs[1]) - base);
						
						sb.append(Misc.implode(infs, "\t") + "\n");
						
//						if (!s.contains(infs[1])){
//							System.err.println("Mismatching input/output!");
//							System.err.println("Input: " + s);
//							System.err.println("Output: " + in);
//							System.err.println("Doc: " + docID);
//							System.exit(-1);
//						}

						in = GDepWrapper.readLine(inStream);
					}
				}
				
				Map<String,String> m = new HashMap<String,String>();
				m.put("enju_data",sb.toString());
				m.put("id", ""+c++);
				res.add(m);
				
				base++;
			}
		}
		
		base = nextBase;
		
		return res;
	}

	@Override
	public void destroy() {
		System.out.println("Closing Enju process.");
		try{
//			this.inStream.close();
			this.outStream.close();
//			this.p.destroy();
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}
}