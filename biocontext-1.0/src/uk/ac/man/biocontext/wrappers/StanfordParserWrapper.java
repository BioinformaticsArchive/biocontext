package uk.ac.man.biocontext.wrappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import martin.common.Pair;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;

import uk.ac.man.biocontext.tools.SentenceSplitter;
import uk.ac.man.textpipe.Annotator;

/**
 * Annotator for running the Stanford constituency tree parser.
 * Required parameter: "model", pointing to a model file (e.g. englishPCFG.ser.gz)
 * @author Martin
 */
public class StanfordParserWrapper extends Annotator {
	private LexicalizedParser lp;

	@Override
	public String[] getOutputFields() {
		return new String[]{"id","stanford_data"};
	}

	@Override
	public void init(Map<String, String> data) {
		if (data == null){
			System.err.println("The Stanford-parser wrapper needs a 'model' parameter; send with --params model=<model path>");
			System.exit(0);
		}
		
		//load and initialize Stanford parser from a model file
		if (data != null)
			this.lp = new LexicalizedParser(data.get("model"));
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String text = data.get("doc_text");		

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();
		
		int c = 0;
		
		SentenceSplitter ssp = new SentenceSplitter(text);
		for (Pair<Integer> sc : ssp){
			String s = text.substring(sc.getX(), sc.getY());
			
			System.out.println(s);
			
			lp.parse(s);
			Tree t = lp.getBestParse();

			String a = t.toString();
			a = a.replaceAll("\\[-?[0123456789\\.]+\\] ", "");

			Map<String,String> m = new HashMap<String, String>();
			m.put("stanford_data",a);
			m.put("id", ""+c++);
			res.add(m);
		}
		
		return res;
	}
}