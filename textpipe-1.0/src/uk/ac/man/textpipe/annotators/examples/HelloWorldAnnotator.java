package uk.ac.man.textpipe.annotators.examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.textpipe.Annotator;

/**
 * Sample annotator used as demonstration of the capabilities of TextPipe. See doc/readme.txt
 * @author Martin
 */
public class HelloWorldAnnotator extends Annotator {

	@Override
	public String[] getOutputFields() {
		return new String[]{"a", "b", "text"};
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		//check if the process request has a parameter "n", 
		//which here controls how many times the output is repeated.
		int n = data.containsKey("n") ? Integer.parseInt(data.get("n")) : 1;
		
		//output object
		List<Map<String,String>> res = new ArrayList<Map<String,String>>();
		
		//repeat...
		for (int i = 0; i < n; i++){
			Map<String,String> m = new HashMap<String,String>();
			
			//add data to to our output object
			m.put("a","Hello");
			m.put("b","World!");
			
			//if processing text documents, the text of the document can be found in doc_text
			//and the document id can be found in doc_id. Just copy these for now (a real
			//application would do something more interesting with them).
			m.put("text",data.get("doc_text"));
			m.put("id",data.get("doc_id"));
			
			res.add(m);
		}
		
		return res;
	}
}
