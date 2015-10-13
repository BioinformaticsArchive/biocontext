package uk.ac.man.biocontext.wrappers.events;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;
import martin.common.InputStreamDumper;

import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.MccloskyWrapper;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class TurkuWrapper extends Annotator {
	private File turkuDir;
	private File tmpDir;
	private Annotator geneClient;
	private Annotator mcCloskyClient;
	public final static String[] outputFields = new String[]{"id","trigger_start", "trigger_end", "trigger_text", "type", "participants"};

	private static final double MIN_GENE_CONFIDENCE = 0.05;

	@Override
	public void init(Map<String, String> data) {
		turkuDir = data.containsKey("turkuDir") ? new File(data.get("turkuDir")) : new File("tools/turku/");
		tmpDir = data.containsKey("tmpDir") ? new File(data.get("tmpDir")) : new File("/tmp/");

//		this.geneClient = TextPipeClient.get(data, "gene", "db001", 57004, "farzin", "data_genes");
//		this.mcCloskyClient = TextPipeClient.get(data, "mcc", "db001", 57010, "farzin", "data_p_mcc");
		this.geneClient = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ArgParser.getParser());
		this.mcCloskyClient = new PrecomputedAnnotator(MccloskyWrapper.outputFields, "farzin", "data_p_mcc", ArgParser.getParser());
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	private boolean writeInputData(Map<String,String> data, File dir){
		String docid = data.containsKey("doc_id") ? data.get("doc_id") : "docid";
		docid = docid.replace('.', '_');
		String text = data.get("doc_text");

		List<Map<String,String>> mcCloskyData = null;
		List<Map<String,String>> geneData = geneClient.process(data);

		//		geneData = EventMineWrapper.modifyTerms(geneData, data.get("doc_text"), data.get("doc_id"));

		removeLowConfGenes(geneData);

		if (geneData.size() == 0){
			return false;
		}

		//For very few documents, we have bad parse data. Catch these
		//cases, and treat them as having no events.
		try{
			mcCloskyData = mcCloskyClient.process(data);
		} catch (IllegalStateException e){
			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.WARNINGS) >= 0)
				System.err.println("error\t1\t" + docid + "\t" + e.toString());
			return false;
		}

		Misc.sort(geneData);
		Misc.sort(mcCloskyData);

		Map<String,String> entityReplacements = getEntityReplacements(mcCloskyData);

		Misc.writeFile(new File(dir, docid + ".txt"), replace(text, entityReplacements));
		Misc.writeFile(new File(dir, docid + ".a1"), replace(getNERText(geneData), entityReplacements));

		String pstree = getGdepText(mcCloskyData, "mcclosky_data");
		String dep = getGdepText(mcCloskyData, "mcclosky_dep");
		String tokens = getGdepText(mcCloskyData, "mcclosky_tokens");

		if (pstree == null){
			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.WARNINGS) >= 0)
				System.err.println("error\t2\t" + docid + "\t");
			return false;
		}

		Misc.writeFile(new File(dir, docid + ".pstree"), replace(pstree, entityReplacements));
		Misc.writeFile(new File(dir, docid + ".dep"), replace(dep, entityReplacements));
		Misc.writeFile(new File(dir, docid + ".tokenized"), replace(tokens, entityReplacements));

		return true;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String text = data.get("doc_text");
		String docid = data.containsKey("doc_id") ? data.get("doc_id") : "docid";

		docid = docid.replace('.', '_');
		
		File tmpDir = Misc.getRandomTmpDir(this.tmpDir);
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.err.println(tmpDir + " - " + docid);
		tmpDir.mkdir();

		boolean inputIsOk;
		try{
			inputIsOk = writeInputData(data, tmpDir);
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.err.println ("error\tTurku crash\t" + docid);
			return new ArrayList<Map<String,String>>(); 
		}

		if (!inputIsOk){
			Misc.delete(tmpDir);
			//			System.err.println(" --- ");
			return new ArrayList<Map<String,String>>();
		}

		Misc.writeFile(new File(tmpDir, "files.list"), docid + "\n");

		convert(tmpDir);

		if (!new File(tmpDir,"out.xml").exists()){
			System.err.println ("error\tTurku crash\t" + docid + "\tout.xml does not exist");

//			Misc.delete(tmpDir);

			return new ArrayList<Map<String,String>>();			
		}
		
		File outDir = new File(tmpDir, "out");
		outDir.mkdir();

		classify(tmpDir, outDir);

		File outFile = new File(new File(outDir, "geniaformat"), docid + ".a2.t1");

		if (outFile.exists()){
			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
				System.err.println ("**** EXISTS ****");
			List<Map<String,String>> res = EventMineWrapper.parseResults(outFile, text);

			Misc.delete(tmpDir);

			return res;

		} else {
			System.err.println ("error\tTurku crash\t" + docid + "\toutfile does not exist");

			Misc.delete(tmpDir);

			return new ArrayList<Map<String,String>>();			
		}
	}

	public List<List<Map<String,String>>> process(List<Map<String,String>> data){
		List<List<Map<String,String>>> res = new ArrayList<List<Map<String,String>>>();

		File dir = Misc.getRandomTmpDir(this.tmpDir);
		dir.mkdir();

		Set<String> docids = new HashSet<String>();
		for (Map<String,String> m : data)
			if (writeInputData(m, dir))
				docids.add(m.get("doc_id"));

		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.err.println(dir + " --- " + martin.common.Misc.unsplit(docids, " "));

		Misc.writeFile(new File(dir, "files.list"), martin.common.Misc.unsplit(docids, "\n") + "\n");

		convert(dir);

		File outDir = new File(dir, "out");
		outDir.mkdir();

		classify(dir, outDir);

		for (Map<String,String> m : data){
			String docid = m.get("doc_id");
			String text = m.get("doc_text");

			if (docids.contains(docid)){
				File outFile = new File(new File(outDir, "geniaformat"), docid + ".a2.t1");

				if (outFile.exists()) {
					//					if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
					System.err.println("*** Exists\t2\t" + docid);
					res.add(EventMineWrapper.parseResults(outFile, text));
				} else {
					//					if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
					System.err.println("*** Exists\t1\t" + docid);
					res.add(process(m));
					//					res.add(new ArrayList<Map<String,String>>());
				}
			} else {
				//				if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
				System.err.println("*** Exists\t0\t" + docid);
				res.add(new ArrayList<Map<String,String>>());
			}
		}

		Misc.delete(dir);

		return res;
	}

	private void convert(File dir) {
		String c = "python convertGenia.py -i " + dir.getAbsolutePath() + " -o " + dir.getAbsolutePath() + "/out.xml -t1 -d " + dir.getAbsolutePath() + "/files.list -k McClosky -p McClosky";
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(c);
		try{
			Process p = Runtime.getRuntime().exec(c, null, new File(this.turkuDir.getAbsolutePath() + "/src/SharedTask/formatConversion"));

			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
				new Thread(new InputStreamDumper(p.getErrorStream(), new File(dir, "conv.err.log"))).start();
			else
				new Thread(new InputStreamDumper(p.getErrorStream())).start();

			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
				new Thread(new InputStreamDumper(p.getInputStream(), new File(dir, "conv.out.log"))).start();
			else
				new Thread(new InputStreamDumper(p.getInputStream())).start();

			p.waitFor();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	private void classify(File dir, File outDir) {
		String c = "python Classify.py --input " + dir.getAbsolutePath() + "/out.xml -o " + outDir + " -p McClosky -t McClosky";
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(c);
		try{
			Process p = Runtime.getRuntime().exec(c, null, new File(this.turkuDir.getAbsolutePath() + "/src/Pipelines"));
			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
				new Thread(new InputStreamDumper(p.getErrorStream(), new File(dir, "classify.err.log"))).start();
			else
				new Thread(new InputStreamDumper(p.getErrorStream())).start();

			if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
				new Thread(new InputStreamDumper(p.getInputStream(), new File(dir, "classify.out.log"))).start();
			else
				new Thread(new InputStreamDumper(p.getInputStream())).start();

			p.waitFor();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	private String replace(String text, Map<String, String> entityReplacements) {
		for (String e : entityReplacements.keySet())
			text = text.replaceAll(e, entityReplacements.get(e));

		return text;
	}

	private Map<String, String> getEntityReplacements(
			List<Map<String, String>> mcCloskyData) {
		Map<String,String> res = new HashMap<String, String>();

		for (Map<String,String> m : mcCloskyData)
			for (String e  : m.get("mcclosky_tokens").split("\t")){
				if (e.contains(" ")){

					String st = Character.isLetterOrDigit(e.charAt(0)) ? "\\b" : "";
					String en = Character.isLetterOrDigit(e.charAt(e.length()-1)) ? "\\b" : "";


					res.put(st + escapeRegexps(e) + en, e.replace(' ', '_'));
					res.put(st + escapeRegexps(e.replace(' ', '\t')) + en, e.replace(' ', '_'));
					//					e = e.replace(' ', '_');
				}
				//				if (e.contains("-")){
				//					res.put(e,e.replace('-', '_'));
				//					e = e.replace('-', '_');
				//				}
			}

		res.put("``", "\"");
		res.put("''", "\"");

		return res;
	}

	private String escapeRegexps(String regexp) {
		return uk.ac.man.entitytagger.generate.GenerateMatchers.escapeRegexp(regexp);
	}

	private String getGdepText(List<Map<String, String>> gdepData, String key) {
		StringBuffer sb = new StringBuffer();
		for (Map<String,String> m : gdepData)
			if (!m.containsKey(key))
				return null;
			else
				sb.append(m.get(key) + "\n");
		return sb.toString();
	}

	private String getNERText(List<Map<String, String>> geneData) {
		StringBuffer sb = new StringBuffer();
		for (Map<String,String> m : geneData){
			sb.append("T" + m.get("id") + "\tProtein " + m.get("entity_start") + " " + m.get("entity_end") + "\t" + m.get("entity_term") + "\n");
		}
		return sb.toString();
	}

	void removeLowConfGenes(List<Map<String, String>> geneData) {
		for (int i = 0; i < geneData.size(); i++)
			if (geneData.get(i).get("confidence") != null && (Double.parseDouble(geneData.get(i).get("confidence"))) < MIN_GENE_CONFIDENCE){
				geneData.remove(i--);				
			}
	}

	@Override
	public boolean isConcurrencySafe() {
		return true;
	}
}