package uk.ac.man.textpipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.documentparser.dataholders.Document;

/**
 * Interface which, if implemented, allows FtTools to call the implementing class for processing of documents
 */
public abstract class Annotator {
	/**
	 * Process a document and other input values (stored in a key/value string map) using the loaded annotator 
	 * @param data input data processed by the tool
	 * @return output data from the tool
	 */
	public abstract List<Map<String,String>> process(Map<String,String> data);

	/**
	 * Process several documents (stored in key/value string maps) using the loaded annotator 
	 * @param data input data processed by the tool
	 * @return output data from the tool
	 */
	public List<List<Map<String,String>>> process(List<Map<String,String>> data){
		List<List<Map<String,String>>> res = new ArrayList<List<Map<String,String>>>();

		for (Map<String,String> m : data)
			res.add(process(m));

		return res;
	}

	/**
	 * Override this method to provide an init function for an annotator for e.g. loading resources on startup
	 * @param data here is a comment
	 */
	public void init(Map<String,String> data) {
	}

	/**
	 * Override to provide a destructor for this tool which would be run when computations are no longer necessary.
	 * This can for example kill any running threads that the annotator depends on.
	 * This method does only have to be implemented if necessary. 
	 */
	public void destroy(){
	}

	/**
	 * @return the names of the fields that this annotator will return as output
	 */
	public abstract String[] getOutputFields();

	/**
	 * Override this method if some of the columns should be indexed when creating tables. 
	 * Columns that should be indexed should be prefixed with the "@" sign. 
	 * @return the names of the fields that this annotator will return as output; fields that should be indexed should be prefixed with @
	 */
	public String[] getOutputFieldsWithIndices(){
		return getOutputFields();
	}
	
	/**
	 * Wrapper for {@code process(Map<String,String>)}, allowing process calls for uk.ac.man.documentparser.dataholders.Document as well.   
	 * @param doc
	 * @return
	 */
	public List<Map<String,String>> processDoc(Document doc){
		Map<String,String> input = doc2map(doc); 		

		return process(input);		
	}

	private Map<String, String> doc2map(Document doc) {

		Map<String, String> input = new HashMap<String,String>();

		if (doc.getID() != null)
			input.put("doc_id",doc.getID());
		else 
			throw new IllegalStateException();

		if (doc.getXml() != null)
			input.put("doc_xml",doc.getXml());
		if (doc.toString() != null)
			input.put("doc_text",doc.toString());
		if (doc.getTitle() != null)
			input.put("doc_title",doc.getTitle());
		if (doc.getAbs() != null)
			input.put("doc_abs",doc.getAbs());
		if (doc.getBody() != null)
			input.put("doc_body",doc.getBody());
		if (doc.getRawContent() != null)
			input.put("doc_raw",doc.getRawContent());
		if (doc.getYear() != null)
			input.put("doc_year",doc.getYear());
		if (doc.getJournal() != null)
			input.put("doc_issn",doc.getJournal().getISSN());
		
		input.put("doc_description", doc.getDescription());
		
		if (doc.getExternalID() != null && doc.getExternalID().getSource() != null)
			input.put("doc_source" ,doc.getExternalID().getSource().toString());
		
		return input;

	}

	/**
	 * Wrapper for {@code process(List<Map<String,String>>)}, allowing process calls for uk.ac.man.documentparser.dataholders.Document as well.   
	 * @param doc
	 * @return
	 */
	public List<List<Map<String,String>>> processDocs(List<Document> docs){
		List<Map<String,String>> inputList = new ArrayList<Map<String,String>>();

		for (Document doc : docs){
			Map<String,String> input = doc2map(doc);
			inputList.add(input);
		}

		return process(inputList);		
	}
	
	/**
	 * @param text
	 * @return the results for this annotator, processed with doc_text = text.
	 */
	public List<Map<String,String>> processText(String text){
		Map<String,String> m = new HashMap<String,String>();
		m.put("doc_text", text);
		return process(m);
	}

	/**
	 * 
	 * @param id
	 * @return the results for this annotator, processed with doc_id = id.
	 */
	public List<Map<String,String>> processDocID(String id){
		Map<String,String> m = new HashMap<String,String>();
		m.put("doc_id", id);
		return process(m);
	}

	/**
	 * @return whether the annotator can safely be used by multiple threads simultaneously. Default, unless overridden, is false.
	 */
	public boolean isConcurrencySafe(){
		return false;		
	}

	/**
	 * 
	 * @return a String describing the service and its input parameters.
	 */
	public String helpMessage(){
		return null;
	}
}
