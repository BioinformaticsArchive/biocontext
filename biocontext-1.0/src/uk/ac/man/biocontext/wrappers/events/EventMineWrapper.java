package uk.ac.man.biocontext.wrappers.events;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import config.Config;

import martin.common.ArgParser;
import martin.common.Pair;
import martin.common.StreamIterator;

import test.ComplexEventPredictor;
import test.NamedEntityPredictor;
import test.RegulationNamedEntityPredictor;
import uk.ac.man.biocontext.tools.SentenceSplitter;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.EnjuWrapper;
import uk.ac.man.biocontext.wrappers.GDepWrapper;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class EventMineWrapper extends Annotator {
	private NamedEntityPredictor nep;
	private RegulationNamedEntityPredictor rnep;
	private ComplexEventPredictor cep;

	private Annotator geneClient;
	private Annotator gdepClient;
	private Annotator enjuClient;

	private File tmpDir;
	private String dep2soCmd;
	public final static String[] outputFields = new String[]{"id","trigger_start", "trigger_end", "trigger_text", "type", "participants"};

	@Override
	public void init(Map<String, String> data) {
		if (data != null && data.containsKey("configFile"))
			Config.CONFIGFILE = data.get("configFile");

		Config.getConfig().VERBOSE = -1;

		String trainFolder = data != null && data.containsKey("modelDir") ? data.get("modelDir") : 
			"/fs/mib/mqbpgmg2/programs/EventMine-comp/train/";
		this.dep2soCmd = data != null && data.containsKey("dep2so") ? data.get("dep2so") :
			"/fs/mib/mqbpgmg2/farzin/tools/EventMine/scripts/dep2so.pl";
		this.tmpDir = data != null && data.containsKey("tmpDir") ? new File(data.get("tmpDir")) :
			new File("/tmp/");

		//		this.geneClient = TextPipeClient.get(data, "gene", "db001", 57004, "farzin", "data_genes");
		//		this.gdepClient = TextPipeClient.get(data, "gdep", "db001", 57011, "farzin", "data_p_gdep");
		//		this.enjuClient = TextPipeClient.get(data, "enju", "db001", 57013, "farzin", "data_p_enju");

		this.geneClient = new PrecomputedAnnotator(GeneCombiner.outputFields,"farzin","data_genes",ArgParser.getParser(),false);
		this.gdepClient = new PrecomputedAnnotator(GDepWrapper.outputFields, "farzin", "data_p_gdep", ArgParser.getParser(),false);
		this.enjuClient = new PrecomputedAnnotator(EnjuWrapper.outputFields, "farzin", "data_p_enju", ArgParser.getParser());

		System.out.println("Loading NEP...");
		this.nep = new NamedEntityPredictor(trainFolder);
		System.out.println("Loading RNEP...");
		this.rnep = new RegulationNamedEntityPredictor(trainFolder);
		System.out.println("Loading CEP...");
		this.cep = new ComplexEventPredictor(trainFolder);
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	public static List<Map<String,String>> modifyTerms(List<Map<String, String>> geneData, String text, String docid) {
		//		int s = 0;
		//		Misc.sort(geneData);
		//		String text2 = martin.common.Misc.loadFile(new File("/fs/san/home/mqbpgmg2/temp/dan/Input_text/" + docid + ".txt"));
		//		List<Map<String,String>> res = new ArrayList<Map<String,String>>();
		for (Map<String,String> m : geneData){
			//			String term = text2.substring(Integer.parseInt(m.get("entity_start")), Integer.parseInt(m.get("entity_end")));
			String term = text.substring(Integer.parseInt(m.get("entity_start")), Integer.parseInt(m.get("entity_end")));

			m.put("entity_term", term);

			//			int s2 = text.indexOf(m.get("entity_term"),s);
			//			if (s2 != -1){
			//				s = s2+1;
			//				m.put("entity_start",""+s2);
			//				m.put("entity_end",""+(s2+term.length()));
			//				System.out.println(docid + "\t" + term + "\t" + s2);
			//				res.add(m);
			//			} else {
			//				System.out.println("XXXXXXXXXXXXXXXX" + "\t" + docid + "\t" + term + "\tXXXXXXXXXXXXXX");
			//			}

		}
		//			m.put("entity_term", text.substring(Integer.parseInt(m.get("entity_start")), Integer.parseInt(m.get("entity_end"))));

		//		return res;
		return geneData;
	}


	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		cep.resetEventIndex();

		String text = data.get("doc_text");
		String docid = data.containsKey("doc_id") ? data.get("doc_id") : "-";

		List<Map<String,String>> geneData = geneClient.process(data);
		List<Map<String,String>> enjuData = null; 
		List<Map<String,String>> gdepData = null; 

		//		modifyTerms(geneData, data.get("doc_text"), data.get("doc_id"));

		if (geneData.size() == 0)
			return new ArrayList<Map<String,String>>(0);		

		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(docid);


		//For very few documents, we have bad Gdep or Enju data. Catch these
		//cases, and treat them as having no events.
		try{
			enjuData = enjuClient.process(data);
			gdepData = gdepClient.process(data);
		} catch (IllegalStateException e){
			System.err.println(e.toString());
			System.err.println("error\tTokyo parse\t" + docid);
			return new ArrayList<Map<String,String>>();
		}

		File tmpDir = Misc.getRandomTmpDir(this.tmpDir);
		tmpDir.mkdir();

		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(tmpDir.getAbsolutePath());

		List<Pair<Integer>> sentenceBreaks = SentenceSplitter.toList(text);

		File textFile = new File(tmpDir, "file.txt"); 
		Misc.writeFile(textFile, text);

		File nerFile = new File(tmpDir, "file.split.gold.so");
		writeNERFile(nerFile,geneData,sentenceBreaks,docid,text.length());

		File splitFile = new File(tmpDir, "file.split.txt");
		writeSplitFile(splitFile,text,sentenceBreaks);

		File gdepFile = new File(tmpDir, "file.split.gdep");
		if (!writeGdepFile(gdepData, gdepFile, splitFile)){
			System.err.println("error\tTokyo gdep\t" + docid);
			return new ArrayList<Map<String,String>>();
		}

		File enjuFile = new File(tmpDir, "file.split.enju.so");
		if (!writeEnjuFile(enjuData, enjuFile)){
			System.err.println("error\tTokyo enju\t" + docid);
			return new ArrayList<Map<String,String>>();
		}

		try{
			nep.run(tmpDir.getAbsolutePath());
			rnep.run(tmpDir.getAbsolutePath());
			cep.run(tmpDir.getAbsolutePath());
		} catch (java.lang.StringIndexOutOfBoundsException e){
			//this happens for very few documents (a few hundred of 10m total), but have
			//proven hard to track down
			System.err.println(e);
			e.printStackTrace();
			System.err.println("error\tTokyo crash\t" + docid);
			return new ArrayList<Map<String,String>>();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.err.println("error\tTokyo crash\t" + docid);
			return new ArrayList<Map<String,String>>();
		}

		File outFile = new File(tmpDir, "file.split.a2t1");
		List<Map<String,String>> res = parseResults(outFile, text);

		Misc.delete(tmpDir);

		return res;
	}

	/**
	 * Reads the .a2-style files and produces a list of events as maps.
	 * @param inFile the input file to be parsed
	 * @param text the text!
	 * @return a list of events as maps.
	 */
	public static List<Map<String, String>> parseResults(File inFile, String text) {
		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		Map<String,Pair<Integer>> triggerCoords = new HashMap<String,Pair<Integer>>();

		StreamIterator data = new StreamIterator(inFile);

		Set<Integer> addedEventHashes = new HashSet<Integer>();
		Set<String> addedIds = new HashSet<String>();

		Map<String,String> triggerTerms = new HashMap<String,String>();
		
//		if (inFile.getName().contains("1313226")){
		
		for (String s : data){
			String[] fs = s.split("\t");
			if (fs[0].startsWith("T")){
				String[] fss = fs[1].split(" ");
				triggerCoords.put(fs[0], new Pair<Integer>(Integer.parseInt(fss[1]), Integer.parseInt(fss[2])));
				if (text ==  null){
					triggerTerms.put(fs[0], fs[2]);
				}
			} else if (fs[0].startsWith("E")){
				String[] fss = fs[1].split(" ");
				String type = fss[0].split(":")[0];
				String trigger = fss[0].split(":")[1];

				if (!triggerCoords.containsKey(trigger))
					throw new IllegalStateException("Could not find trigger data for " + trigger + " in " + inFile.getAbsolutePath() + " - maybe triggers were not detailed prior to events?");

				String triggerText = null;
				if (text != null)
					triggerText = text.substring(triggerCoords.get(trigger).getX(), triggerCoords.get(trigger).getY());
				else
					triggerText = triggerTerms.get(trigger);

				String parts = getParticipants(fss);

				Map<String,String> m = new HashMap<String,String>();

				m.put("id", fs[0]);
				m.put("trigger_start", ""+triggerCoords.get(trigger).getX());
				m.put("trigger_end", ""+triggerCoords.get(trigger).getY());
				m.put("trigger_text", triggerText);
				m.put("type", type);
				m.put("participants", parts);
				m.put("negated", "0");
				m.put("speculated", "0");

				String hashString = m.get("trigger_start") + m.get("trigger_end") + m.get("type") + m.get("participants");
				int hash = hashString.hashCode();

				if (!addedEventHashes.contains(hash)){
					res.add(m);
					addedEventHashes.add(hash);
					addedIds.add(fs[0]);
				}

			} else if (fs[0].startsWith("M")){
				//TODO
				String[] fss = fs[1].split(" ");
				String id = fss[1];
				if (addedIds.contains(id)){
					Map<String,String> eventMap = Misc.getByID(res, id);
					if (eventMap == null){
						throw new IllegalStateException("Event not found in " + inFile.getAbsolutePath() + " " + id);
					}

					if (fss[0].equals("Negation")){
						eventMap.put("negated", "1");
					}

					if (fss[0].equals("Speculation")){
						eventMap.put("speculated", "1");
					} 
				}

			} else if (fs[0].startsWith("*") || s.length() == 0){
			} else {
				throw new IllegalStateException("Unrecognized EventMine output line: " + s);
			}
//		}
		}

		return res;
	}

	private static String getParticipants(String[] fss) {
		int numThemes = 1;

		String cause="";
		String themes="";
		//		String site="";

		for (int i = 1; i < fss.length; i++){
			String s = fss[i];
			if (s.startsWith("Cause:"))
				cause=s.substring(6);
			else if (s.startsWith("Site") || s.startsWith("ToLoc") || s.startsWith("CSite") || s.length() == 0 || s.startsWith("AtLoc")){
				//ignore
			} else if (s.startsWith("Theme")){
				if (numThemes == 1 && s.startsWith("Theme:")){
					themes = s.substring(6);
					numThemes++;
				} else if (s.startsWith("Theme" + numThemes + ":")){
					themes += "," + s.substring(("Theme" + numThemes + ":").length());
					numThemes++;
				} else {
					throw new IllegalStateException("Could not recognize EventMine output '" + s + "'");
				}
			} else {
				throw new IllegalStateException("Could not recognize EventMine output '" + s + "'");
			}
		}

		return cause + "|" + themes;
		//		return cause + "|" + site + "|" + themes;
	}

	private boolean writeEnjuFile(List<Map<String, String>> enjuData, File enjuFile) {
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(enjuFile));

			for (int i = 0; i < enjuData.size(); i++){
				Map<String,String> g = Misc.getByID(enjuData, i);

				if (g == null){
					return false;
				}

				outStream.write(g.get("enju_data") + "\n");
			}

			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return true;		
	}

	private boolean writeGdepFile(List<Map<String, String>> gdepData, File gdepFile, File splitFile) {
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(gdepFile));

			for (int i = 0; i < gdepData.size(); i++){
				Map<String,String> g = Misc.getByID(gdepData, i);
				if (g == null)
					return false;

				//the replace here is to fix a bug in eventmine, where quotation marks are not escaped in dep2so.pl
				outStream.write(g.get("gdep_data").replace('\"', '\'') + "\n");
			}

			outStream.close();

			Runtime.getRuntime().exec(dep2soCmd + " " + gdepFile.getAbsolutePath() + " " + splitFile.getAbsolutePath() + " " + gdepFile.getAbsolutePath() + ".so").waitFor();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return true;
	}

	private void writeSplitFile(File splitFile, String text,
			List<Pair<Integer>> sentenceBreaks) {

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(splitFile));

			text = text.replace('\n', ' ');

			for (Pair<Integer> p : sentenceBreaks)
				outStream.write(text.substring(p.getX(), Math.max(p.getX(), p.getY()-1)) + "\n");

			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void writeNERFile(File nerFile, List<Map<String, String>> geneData,
			List<Pair<Integer>> sentenceBreaks, String docid, int textLength) {

		if (docid == null)
			docid="-";

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(nerFile));

			outStream.write("0\t" + textLength + "\tarticle pmid=\"" + docid + "\"\n");
			for (Pair<Integer> p : sentenceBreaks)
				outStream.write(p.getX() + "\t" + p.getY() + "\tsentence\n");
			for (Map<String,String> g : geneData)
				outStream.write(g.get("entity_start") + "\t" + g.get("entity_end") + "\tProtein id=\"T" + g.get("id") + "\"\n");

			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public boolean isConcurrencySafe() {
		return true;
	}
}
