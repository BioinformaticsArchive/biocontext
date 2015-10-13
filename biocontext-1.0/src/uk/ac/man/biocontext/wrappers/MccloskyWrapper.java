package uk.ac.man.biocontext.wrappers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeBankGrammaticalStructureWrapper;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TypedDependency;

import martin.common.InputStreamDumper;
import martin.common.Pair;

import uk.ac.man.biocontext.dataholders.Node;
import uk.ac.man.biocontext.dataholders.Exception.ParseFailedException;
import uk.ac.man.biocontext.tools.SentenceSplitter;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipe.VerbosityLevel;
import uk.ac.man.textpipe.server.KillTimer;

public class MccloskyWrapper extends Annotator{
	private File mccloskyDir;
	private File tmpDir;
//	private Random r = new Random();
//	private static final int MAX_LINE_LENGTH = 400;
	private static final int MAX_WAIT_TIME = 40 * 1000;
	public static final String[] outputFields = new String[] {"id","mcclosky_data","mcclosky_dep","mcclosky_tokens"};
	
	@Override
	public void init(java.util.Map<String,String> data) {
		mccloskyDir = new File(data.get("mccloskyDir"));
		tmpDir = data.containsKey("tmpDir") ? new File(data.get("tmpDir")) : new File("/tmp/");
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	public int splitAndWriteTempFile(File f, String text) {
		SentenceSplitter ssp = new SentenceSplitter(text);

		int c = 0; 
		
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(f));

			for (Pair<Integer> p : ssp){
				List<Pair<Integer>> splitSentences = new ArrayList<Pair<Integer>>();
				splitSentences.add(p);

				//check if the sentence is too long for Gdep
//				boolean tooLong = p.getY() - p.getX() > MAX_LINE_LENGTH;
//
//				while (tooLong){
//					tooLong = false;
//					for (int i = 0; i < splitSentences.size(); i++)
//						if (splitSentences.get(i).getY() - splitSentences.get(i).getX() > MAX_LINE_LENGTH){
//							//split the sentence into smaller parts
//							tooLong = true;
//							int x = splitSentences.get(i).getX();
//							int y = splitSentences.remove(i).getY();
//							int middle = (y-x)/2 + x;
//							middle = text.indexOf(" ",middle);
//							splitSentences.add(i++, new Pair<Integer>(x,middle));
//							splitSentences.add(i++, new Pair<Integer>(middle,y));
//						}
//				}

				for (Pair<Integer> p2 : splitSentences){
					c++;
					outStream.write("<s> " + text.substring(p2.getX(),p2.getY()).replace("\n", " ") +" </s>\n\n");
					if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0)
						System.out.println("<s> " + text.substring(p2.getX(),p2.getY()).replace("\n", " ") +" </s>\n\n");
				}
			}

			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return c;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		if (TextPipe.verbosity.compareTo(TextPipe.VerbosityLevel.DEBUG) >= 0)
			System.out.println(data.get("doc_id"));
		
		String text = data.get("doc_text");
		text = text.replace("-", " - ");
		text = text.replace("/", " / ");

		File tmpFile = Misc.getRandomTmpDir(this.tmpDir);

		int numSentences = splitAndWriteTempFile(tmpFile, text);

		String c = mccloskyDir.getAbsolutePath() + "/parse.sh " + tmpFile.getAbsolutePath();
		StringBuffer sb = new StringBuffer();

		try{
			Process p = Runtime.getRuntime().exec(c, null, mccloskyDir);
			new Thread(new InputStreamDumper(p.getInputStream(),sb)).start();
			new Thread(new InputStreamDumper(p.getErrorStream(),System.err)).start();

			KillTimer kt = new KillTimer(MAX_WAIT_TIME);
			kt.addProcess(p);
//			kt.addCloseable(p.getInputStream());
//			kt.addCloseable(p.getOutputStream());
//			kt.addCloseable(p.getErrorStream());
			new Thread(kt).start();

			p.waitFor();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		Misc.delete(tmpFile);
		
		String[] lines = sb.toString().split("\n");
		sb = new StringBuffer();

		for (String l : lines)
			if (l.length()>0)
				sb.append(l + "\n");
		
		lines = sb.toString().split("\n");
		
		if (lines.length != numSentences){
			System.err.println("Mcclosky failed, for id " + data.get("doc_id"));
//			return new ArrayList<Map<String,String>>();
		}

		tmpFile = Misc.writeTempFile(tmpDir, sb.toString());
		String[] dep = null;
		try {
			dep = getDep(tmpFile);
		} catch (Exception e){
			System.err.println("Mcclosky failed, for id " + data.get("doc_id"));
			return new ArrayList<Map<String,String>>();
		}
		Misc.delete(tmpFile);

		List<Map<String, String>> res = new ArrayList<Map<String,String>>();
		int counter = 0;
		for (String line : lines){
			if (line.length()>0){
				Map<String,String> h = new HashMap<String, String>();
				h.put("id", ""+counter);
				h.put("mcclosky_data", line);
				h.put("mcclosky_dep", dep[counter]);
				h.put("mcclosky_tokens", martin.common.Misc.implode(getTokens(line), "\t"));
				res.add(h);
				counter++;
			}
		}

		return res ;
	}

	private String[] getTokens(String line) {
		try {
			Node r = Node.makeTree(line);
			List<Node> tokens = r.getLeaves();
			String[] res = new String[tokens.size()];
			int c = 0;
			for (Node n : tokens)
				res[c++] = n.getData().get("text");
			return res;
		} catch (ParseFailedException e) {
			return null;
		}
	}

	private String[] getDep(File tmpFile) {
		MemoryTreebank tb = new MemoryTreebank(new TreeNormalizer());
		tb.processFile(tmpFile);
		Collection<GrammaticalStructure> gsBank = new TreeBankGrammaticalStructureWrapper(tb, false);

		String[] res = new String[gsBank.size()];
		int c = 0;

		for (GrammaticalStructure gs : gsBank) {
			Tree t;
			if (gsBank instanceof TreeBankGrammaticalStructureWrapper) {
				t = ((TreeBankGrammaticalStructureWrapper) gsBank).getOriginalTree(gs);
			} else {
				t = gs.root(); // recover tree
			}

//			System.out.println("---------- CCprocessed dependencies ----------");
			List<TypedDependency> deps = gs.typedDependenciesCCprocessed(true);
//			EnglishGrammaticalStructure.printDependencies(gs, deps, t, false, false);
			res[c++] = EnglishGrammaticalStructure.dependenciesToString(gs, deps, t, false, false);
		}

		return res;
	}

	public void printchars(List<Map<String,String>> data){
		for (Map<String,String> m : data)
			for (String v : m.values()){
				System.out.println(v);
				for (int i = 0; i < v.length(); i++)
					System.out.println(i + "\t'" + v.charAt(i) + "'\t" + ((int) v.charAt(i)));
				System.out.println();
			}
	}

	@Override
	public boolean isConcurrencySafe() {
		return true;
	}
}
