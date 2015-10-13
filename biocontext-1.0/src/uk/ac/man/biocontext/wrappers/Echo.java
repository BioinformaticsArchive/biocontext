package uk.ac.man.biocontext.wrappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.ac.man.textpipe.Annotator;

public class Echo extends Annotator {
	private int numRepetitions=1;
	String[] outputFields = new String[]{};		

	@Override
	public void init(java.util.Map<String,String> data) {
		if (data != null && data.containsKey("num"))
			this.numRepetitions = Integer.parseInt(data.get("num"));
		
		if (data != null && data.containsKey("fields")){
			outputFields = data.get("fields").split("\\|");
		}
	}
	
	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		for (int i = 0; i < numRepetitions; i++)
			res.add(data);
		
		return res;
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}
	
	@Override
	public boolean isConcurrencySafe() {
		return true;
	}
}
